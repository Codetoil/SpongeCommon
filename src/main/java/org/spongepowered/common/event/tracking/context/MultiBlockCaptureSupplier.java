/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.event.tracking.context;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEventData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.common.block.BlockUtil;
import org.spongepowered.common.block.SpongeBlockSnapshot;
import org.spongepowered.common.block.SpongeBlockSnapshotBuilder;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.interfaces.world.IMixinWorldServer;
import org.spongepowered.common.mixin.core.block.state.MixinStateImplementation;
import org.spongepowered.common.util.SpongeHooks;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.BlockChange;
import org.spongepowered.common.world.SpongeBlockChangeFlag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

public final class MultiBlockCaptureSupplier implements ICaptureSupplier {

    @Nullable private LinkedListMultimap<BlockPos, SpongeBlockSnapshot> multimap;
    @Nullable private ListMultimap<BlockPos, BlockEventData> scheduledEvents;
    @Nullable private LinkedListMultimap<BlockPos, EventTransaction> orderedTransactions;
    @Nullable private List<SpongeBlockSnapshot> snapshots;
    @Nullable private Set<BlockPos> usedBlocks;
    private int transactionIndex = -1; // These are used to keep track of which snapshot is being referred to as "most recent change"
    private int snapshotIndex = -1;    // so that we can appropriately cancel or discard or apply specific event transactions
    private boolean hasMulti = false;
    @Nullable private EventTransaction lastTransaction;

    public MultiBlockCaptureSupplier() {
    }

    /**
     * Captures the provided {@link BlockSnapshot} into a {@link Multimap} backed collection.
     * The premise is that each {@link BlockPos} normally has a single {@link BlockChange},
     * with the exceptions of certain few cases where multiple changes can occur for the same
     * position. The larger issue is that while the multiple changes are tracked, the desired
     * flag of changes does not result in a valid {@link BlockChange}, and therefor an invalid
     * {@link ChangeBlockEvent} is generated, potentially leading to duplication bugs with
     * protection plugins. As a result, the consuming {@link BlockSnapshot} is placed into
     * a {@link ListMultimap} keyed by the {@link BlockPos}, and if there are multiple snapshots
     * per {@link BlockPos}, {@link #hasMultiChanges()} will be {@code true}, and this method
     * will return {@code true}.
     *
     * @param snapshot The snapshot being captured
     * @param newState The most current new IBlockState to calculate the BlockChange flag
     * @return True if the block position has previously not been modified or captured yet
     */
    public boolean put(BlockSnapshot snapshot, IBlockState newState) {
        // Start by figuring out the backing snapshot. In all likelyhood, we could just cast, but we want to be safe
        final SpongeBlockSnapshot backingSnapshot = getBackingSnapshot(snapshot);
        // Get the key of the block position, we know this is a pure block pos and not a mutable one too.
        final BlockPos blockPos = backingSnapshot.getBlockPos();
        if (this.usedBlocks == null) { // Means we have a first usage. All three fields are null
            // At this point, we know we have not captured anything and
            // can just populate the normal list.
            this.usedBlocks = new HashSet<>();

            this.usedBlocks.add(blockPos);
            this.addSnapshot(backingSnapshot);
            return true;
        }
        // This isn't our first rodeo...
        final boolean added = this.usedBlocks.add(blockPos); // add it to the set of positions already used and use the boolean
        if (this.hasMulti) {
            // Means we've already got multiple changes per position once before.
            // Likewise, the used blocks, snapshots and multimap will NOT be null.
            // more fasts, we know we have multiple block positions.
            // And we can find out if this is the first time we
            if (added) {
                // If the position hasn't been captured yet, that means we need to add it as an original
                // snapshot being changed, for the list usage.
                this.addSnapshot(backingSnapshot);
            }
            // we don't have to
            this.multimap.put(blockPos, backingSnapshot);

            // If the position is duplicated, we need to update the original snapshot of the now incoming block change
            // in relation to the original state (so if a block was set to air, then afterwards set to piston head, it should go from break to modify)
            if (!added) {
                associateBlockChangeForPosition(newState, blockPos);
            }
            return added;
        }
        // We have not yet checked if this incoming snapshot is a duplicate position
        if (!added) {
            // Ok, means we have a multi change on a same position, now to use the multimap
            // for probably the first time.
            if (this.multimap == null) {
                // If the multimap hasn't been populated yet, well, now we have to populate the multimap and eliminate the list.
                this.multimap = LinkedListMultimap.create(); // LinkedListMultimap is insertion order respective, so the backed lists per
                // Now to populate it from the previously used list of snapshots...
                for (SpongeBlockSnapshot existing : this.snapshots) { // Ignore snapshots potentially being null, it will never be null at this point.
                    this.multimap.put(existing.getBlockPos(), existing);
                }
            }
            // And place the snapshot into the multimap.
            this.multimap.put(blockPos, backingSnapshot);
            // Now we can re-evaluate the modified block position
            // If the position is duplicated, we need to update the original snapshot of the now incoming block change
            // in relation to the original state (so if a block was set to air, then afterwards set to piston head, it should go from break to modify)
            associateBlockChangeForPosition(newState, blockPos);
            // Flip the boolean to have fasts for next entry
            this.hasMulti = true;
            return false;
        }
        // At this point, we haven't captured the block position yet.
        // and we can check if the list is null.
        this.addSnapshot(backingSnapshot);
        // And this is the only time that we return true, if we have not caught multiple transactions per position before.
        return true;
    }

    private void addSnapshot(SpongeBlockSnapshot backingSnapshot) {
        if (this.snapshots == null) {
            this.snapshots = new ArrayList<>();
        }
        this.snapshots.add(backingSnapshot);
        this.snapshotIndex++;
    }

    /**
     * Associates the desired block state {@link BlockChange} in comparison to the
     * already guaranteed original {@link SpongeBlockSnapshot} for proper event
     * creation when multiple block changes exist for the provided {@link BlockPos}.
     *
     * <p>Note: This method <strong>requires</strong> that {@link #hasMulti} is {@code true},
     * otherwise the state of {@link #multimap} may be {@code null} and cause an NPE.</p>
     *
     * @param newState The incoming block change to compare to change
     * @param blockPos The block position to get the backing list from the multimap
     */
    private void associateBlockChangeForPosition(IBlockState newState, BlockPos blockPos) {
        final List<SpongeBlockSnapshot> list = this.multimap.get(blockPos);
        if (list != null && !list.isEmpty()) {
            final SpongeBlockSnapshot originalSnapshot = list.get(0);
            TrackingUtil.associateBlockChangeWithSnapshot(PhaseTracker.getInstance().getCurrentState(), newState.getBlock(),
                BlockUtil.toNative(originalSnapshot.getState()), originalSnapshot);
        }
    }

    public boolean hasMultiChanges() {
        return this.hasMulti;
    }

    /**
     * Gets an <b>unmodifiable</b> {@link List} of the original
     * {@link BlockSnapshot}s being changed for their respective
     * {@link BlockPos Block Positions}. The list is self updating
     * and the {@link BlockSnapshot}s themselves are self updating
     * based on the current processes within the PhaseTracker's
     * {@link IPhaseState} the game is processing. The reasons for
     * this list to be unmodifiable except by this object are as follows:
     * <ul>
     *     <li>Submitted {@link BlockSnapshot}s are to be added by the
     *     {@link #put(BlockSnapshot, IBlockState)} method.</li>
     *     <li>Adding multiple {@link BlockSnapshot}s per {@link BlockPos}
     *     results in an internal restructuring of storage such that a
     *     {@link Multimap} is created to keep track of intermediary
     *     {@link BlockSnapshot}s. By this nature, the list cannot be modified
     *     except by this capture object.</li>
     *     <li>Removing a {@link BlockSnapshot} is only applicable via
     *     {@link #prune(BlockSnapshot)} or {@link #clear()}. This is to
     *     allow sanity checking for multimap purposes and garbage cleanup
     *     when necessary.</li>
     *     <li>The creation of {@link ChangeBlockEvent}s requires a
     *     {@link Transaction} to be created, and plugins are only
     *     exposed the {@link Transaction#getOriginal()} as the first
     *     {@link BlockSnapshot} that would exist in this list. Intermediary
     *     {@link BlockSnapshot} changes for that postiion are internally
     *     utilized to process physics, but are not exposed to the event.</li>
     * </ul>
     *
     * @return An unmodifiable list of first block originals being changed
     */
    public final List<SpongeBlockSnapshot> get() {
        return this.snapshots == null ? Collections.emptyList() : Collections.unmodifiableList(this.snapshots);
    }

    public final void prune(BlockSnapshot snapshot) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Unexpected pruning on an empty capture object for position " + snapshot.getPosition());
        }
        // Start by figuring out the backing snapshot. In all likelyhood, we could just cast, but we want to be safe
        final SpongeBlockSnapshot backingSnapshot = getBackingSnapshot(snapshot);
        // Get the key of the block position, we know this is a pure block pos and not a mutable one too.
        final BlockPos blockPos = backingSnapshot.getBlockPos();
        // Check if we have a multi-pos
        if (this.hasMulti) {
            pruneFromMulti(backingSnapshot, blockPos);
            return;
        }
        pruneSingle(backingSnapshot, blockPos);
    }

    private void pruneSingle(final SpongeBlockSnapshot backingSnapshot, final BlockPos blockPos) {
        if (this.usedBlocks == null) {
            // means we didn't actually capture???
            throw new IllegalStateException("Expected to remove a single block change that was supposed to be captured....");
        }
        if (this.snapshots == null) {
            // also means we didn't capture.... wtf is going on at this point?
            throw new IllegalStateException("Expected to remove a single block change that was supposed to be captured....");
        }
        this.usedBlocks.remove(blockPos);
        this.snapshots.remove(backingSnapshot); // Should be the same snapshot used
    }

    private void pruneFromMulti(final SpongeBlockSnapshot backingSnapshot, final BlockPos blockPos) {
        final List<SpongeBlockSnapshot> snapshots = this.multimap.get(blockPos);
        if (snapshots != null) {
            for (final Iterator<SpongeBlockSnapshot> iterator = snapshots.iterator(); iterator.hasNext(); ) {
                final SpongeBlockSnapshot next = iterator.next();
                if (next.getState().equals(backingSnapshot.getState())) {
                    iterator.remove();
                    break;
                }
            }
            // If the list view is now empty, we need to prune the position from the multimap
            if (snapshots.isEmpty()) {
                this.multimap.removeAll(blockPos);
                // And then prune the snapshot from the list of firsts
                for (final Iterator<SpongeBlockSnapshot> firsts = this.snapshots.iterator(); firsts.hasNext(); ) {
                    final SpongeBlockSnapshot next = firsts.next();
                    if (next.equals(backingSnapshot)) {
                        firsts.remove();
                        // And if it's been found, remove the position from the used blocks as well.
                        this.usedBlocks.remove(blockPos);
                        break;
                    }
                }
                if (this.snapshots.isEmpty()) {
                    this.multimap = null;

                }
            }
        }
    }


    private SpongeBlockSnapshot getBackingSnapshot(BlockSnapshot snapshot) {
        SpongeBlockSnapshot backingSnapshot;
        if (!(snapshot instanceof SpongeBlockSnapshot)) {
            backingSnapshot = new SpongeBlockSnapshotBuilder().from(snapshot).build();
        } else {
            backingSnapshot = (SpongeBlockSnapshot) snapshot;
        }
        return backingSnapshot;
    }

    /**
     * Returns {@code true} if there are no captured objects.
     *
     * @return {@code true} if empty
     */
    @Override
    public final boolean isEmpty() {
        return this.snapshots == null || this.snapshots.isEmpty();
    }

    /**
     * If not empty, activates the {@link BiConsumer} then clears all captures.
     * The catch with this is that as the underlying list is guaranteed
     * to be the first {@link BlockSnapshot} change, multiple changes can exist
     * for an individual {@link BlockPos}, such that the multi-map needs to
     * provide said information to the consumer.
     *
     * <p>The first {@link List} parameter is identical to having been
     * built from the first elements of each list from the second parameter
     * of {@link Map} where {@link Map#values()} returns the insertion order
     * preserved {@link List} of {@link BlockSnapshot}s for the backed
     * {@link BlockPos}. In simpler terms, the {@link List} is built from
     * the first elements of each list from the {@link Map}s values.</p>
     *
     * @param consumer The consumer to activate
     */
    @SuppressWarnings("UnstableApiUsage") // Guava marks Multimaps.asMap as Beta features
    public final void acceptAndClearIfNotEmpty(BiConsumer<List<? extends BlockSnapshot>, Map<BlockPos, List<BlockSnapshot>>> consumer) {
        if (this.multimap != null) {
            final List<? extends BlockSnapshot> blockSnapshots = get();
            // Since multimaps provide a view when asMap is called, we need to recreate the collection
            // of the map to pass into the consumer
            final Map<BlockPos, List<SpongeBlockSnapshot>> view = Multimaps.asMap(this.multimap);
            final Map<BlockPos, List<BlockSnapshot>> map = new LinkedHashMap<>(view.size());
            for (Map.Entry<BlockPos, List<SpongeBlockSnapshot>> entryView : view.entrySet()) {
                map.put(entryView.getKey(), new ArrayList<>(entryView.getValue()));
            }
            this.multimap.clear(); // Clean captured lists before they get potentially contaminated by processing.
            consumer.accept(blockSnapshots, map); // Accept the list and map
        }
    }

    /*
    Begin the more enhanced block tracking. This is only used by states that absolutely need to be able to track certain changes
    that involve more "physics" related transactions, such as neighbor notification tracking, tile entity tracking, and
    normally, intermediary transaction tracking. Because of these states, we need to envelop the information relating to:
    - The most recent block change, if it has been a change that was applied
    - The most recent tile entity being captured
    - The most recent neighbor notification in the order in which it is being applied to in comparison with the most recent block change

    In some rare cases, some block changes may take place after a neighbor notification is submitted, or a tile entity is being replaced.
    To acommodate this, when such cases arise, we attempt to snapshot any potential transactions that may take place prior to their
    blocks being changed, allowing us to take full snapshots of tile entities in the event a complete restoration is required.
    This is achieved through captureNeighborNotification and logTileChange.
     */

    private EventTransaction createEvent() {
        return createEvent((BlockChange) null);
    }

    private EventTransaction createEvent(BlockPos sourcePos) {
        final List<EventTransaction> eventTransactions = this.orderedTransactions.get(sourcePos);
        final SpongeBlockSnapshot attached = eventTransactions.isEmpty() ? (SpongeBlockSnapshot) BlockSnapshot.NONE : eventTransactions.get(eventTransactions.size() - 1).changedSnapshot; // get the last element as our latest snapshot
        final int transactionIndex = ++this.transactionIndex;
        final EventTransaction transaction = new EventTransaction(transactionIndex, this.snapshotIndex, attached, null);
        this.lastTransaction = transaction;
        return transaction;
    }

    private EventTransaction createEvent(@Nullable BlockChange change) {
        final SpongeBlockSnapshot attachedSnapshot = this.hasMulti // Sanity check here if we have multiple captures
                                                     ? this.multimap.entries().get(this.multimap.entries().size() - 1).getValue() // If we do, get the last logged block change
                                                     : this.snapshots != null // Otherwise, check if the list of snapshots is not null
                                                       ? this.snapshots.get(this.snapshots.size() - 1) // And get the last captured snapshot.
                                                       : (SpongeBlockSnapshot) BlockSnapshot.NONE; // Otherwise, well, we're kinda sol.
        final int transactionIndex = ++this.transactionIndex;
        final EventTransaction transaction = new EventTransaction(transactionIndex, this.snapshotIndex, attachedSnapshot, change);
        this.lastTransaction = transaction;
        return transaction;
    }

    private void logTransaction(BlockPos target, EventTransaction transaction) {
        if (this.orderedTransactions == null) {
            this.orderedTransactions = LinkedListMultimap.create();
        }
        this.orderedTransactions.put(target, transaction);
    }

    public void captureNeighborNotification(PhaseContext<?> context, IMixinWorldServer mixinWorldServer, BlockPos notifyPos,
        IBlockState iblockstate,
        Block sourceBlock, BlockPos sourcePos) {
        final EventTransaction transaction = createEvent(sourcePos);
        // Finally, set the notification to the transaction
        transaction.notificationSnapshot = context.getNeighborNotificationSource();
        transaction.notification = new NeighborNotification(mixinWorldServer, iblockstate, notifyPos, sourceBlock, sourcePos);
        if (notifyPos.getX() == -1100 && notifyPos.getY() == 58 && notifyPos.getZ() == -496 && sourcePos.getX() == -1099 && sourcePos.getY() == -58 && sourcePos.getZ() == -496) {
            System.err.println("derp");
        }
        logTransaction(sourcePos, transaction);
    }

    public void logBlockChange(SpongeBlockSnapshot originalBlockSnapshot, IBlockState newState, BlockPos pos, @Nullable TileEntity tileEntity) {
        this.put(originalBlockSnapshot, newState); // Always update the snapshot index before the block change is tracked
        final EventTransaction transaction = createEvent(tileEntity != null ? BlockChange.BREAK : null);
        if (tileEntity != null) {
            transaction.removedTileEntity = tileEntity; // The tile entity is going to be removed by the transaction if successfully handled
        }
        transaction.newState = newState;
        logTransaction(pos, transaction);
    }

    public void logTileChange(IMixinWorldServer mixinWorldServer, BlockPos pos, @Nullable TileEntity currenTile, @Nullable TileEntity tileEntity) {
        final WorldServer world = (WorldServer) mixinWorldServer;
        final IBlockState current = world.getBlockState(pos);

        final SpongeBlockSnapshot snapshot = mixinWorldServer.createSpongeSnapshotForTileEntity(current, pos, BlockChangeFlags.ALL, currenTile);
        final BlockChange flag = currenTile != null
                                 ? tileEntity != null
                                   ? BlockChange.MODIFY // If both new and old are not null, it's a modification
                                   : BlockChange.BREAK  // If new is null and old is not, we're breaking the tile entity
                                 : tileEntity != null
                                   ? BlockChange.PLACE // If the replacing tile entity si not null AND the original is null, well, we're placing
                                   : BlockChange.DECAY; // If both are null (why are we even here?) it's decay to prevent logic in other places
        snapshot.blockChange = flag;
        this.put(snapshot, current);
        final EventTransaction transaction = createEvent(flag);
        transaction.removedTileEntity = currenTile;
        transaction.addedTileEntity = tileEntity;
        logTransaction(pos, transaction);
    }

    public void cancelTransaction(BlockSnapshot original) {
        if (this.orderedTransactions == null || this.orderedTransactions.isEmpty()) {
            return;
        }

        final BlockPos blockPos = ((SpongeBlockSnapshot) original).getBlockPos();

        final WorldServer worldServer = ((SpongeBlockSnapshot) original).getWorldServer();
        final List<EventTransaction> values = this.orderedTransactions.values();
        for (ListIterator<EventTransaction> iterator = values.listIterator(values.size()); iterator.hasPrevious();) {
            final EventTransaction next = iterator.previous(); // We have to iterate in reverse order due to rollbacks
            if (!next.isCancelled) {
                next.cancel(worldServer, blockPos);
            }

        }
    }

        @Override
    public int hashCode() {
        return Objects.hashCode(this.snapshots);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MultiBlockCaptureSupplier other = (MultiBlockCaptureSupplier) obj;
        return Objects.equals(this.multimap, other.multimap);
    }


    @Override
    public String toString() {
        return com.google.common.base.MoreObjects.toStringHelper(this)
            .add("Captured", this.snapshots == null ? 0 : this.snapshots.size())
            .toString();
    }

    public void clear() {
        this.hasMulti = false;
        if (this.multimap != null) {
            this.multimap.clear();
            this.multimap = null;
        }
        if (this.snapshots != null) {
            this.snapshots.clear();
            this.snapshots = null;
        }
        if (this.usedBlocks != null) {
            this.usedBlocks.clear();
        }
        if (this.scheduledEvents != null) {
            this.scheduledEvents.clear();
        }
        this.lastTransaction = null;
        this.snapshotIndex = -1;
        this.transactionIndex = -1;
    }

    public void restoreOriginals() {
        if (this.snapshots != null && !this.snapshots.isEmpty()) {
            for (SpongeBlockSnapshot original : Lists.reverse(this.snapshots)) {
                original.restore(true, BlockChangeFlags.NONE);
            }
            this.clear();
        }
    }

    public Transaction<BlockSnapshot> createTransaction(SpongeBlockSnapshot snapshot) {
        final WorldServer worldServer = snapshot.getWorldServer();
        final BlockPos blockPos = snapshot.getBlockPos();
        final IBlockState newState = worldServer.getBlockState(blockPos);
        final IBlockState newActualState = newState.getActualState(worldServer, blockPos);
        final BlockSnapshot newSnapshot =
            ((IMixinWorldServer) worldServer).createSpongeBlockSnapshot(newState, newActualState, blockPos, BlockChangeFlags.NONE);
        // Up until this point, we can create a default Transaction
        if (this.hasMulti) { // But we need to check if there's any intermediary block changes...
            // And because multi is true, we can be sure the multimap is populated at least somewhere.
            final List<SpongeBlockSnapshot> intermediary = this.multimap.get(blockPos);
            if (!intermediary.isEmpty()) {
                // We need to make a carbon copy of the list since it's technically a key view list
                // within the multimap, so, if the multimap is cleared, at the very least, the list will
                // not be cleared.
                return new Transaction<>(snapshot, newSnapshot, ImmutableList.copyOf(intermediary));
            }
        }
        return new Transaction<>(snapshot, newSnapshot);
    }

    public boolean trackEvent(BlockPos pos, BlockEventData blockEventData) {
        if (this.usedBlocks != null && this.usedBlocks.contains(pos)) {
            if (this.scheduledEvents == null) {
                this.scheduledEvents = LinkedListMultimap.create();
            }
            this.scheduledEvents.put(pos.toImmutable(), blockEventData);
            return true;
        }
        return false;
    }

    public ListMultimap<BlockPos, BlockEventData> getScheduledEvents() {
        return this.scheduledEvents == null ? ArrayListMultimap.create(4, 4) : ArrayListMultimap.create(this.scheduledEvents);
    }

    @SuppressWarnings("unchecked")
    public boolean processTransactions(List<Transaction<BlockSnapshot>> transactions, PhaseContext<?> phaseContext, boolean noCancelledTransactions,
        ListMultimap<BlockPos, BlockEventData> scheduledEvents, int currentDepth) {

        final IPhaseState phaseState = phaseContext.state;
        int targetIndex = 0;
        if (this.orderedTransactions == null || this.orderedTransactions.isEmpty()) {
            boolean hasEvents = false;
            if (!scheduledEvents.isEmpty()) {
                hasEvents = true;
            }
            for (Transaction<BlockSnapshot> transaction : transactions) {
                if (!transaction.isValid()) {
                    continue;
                }
                final BlockPos pos = hasEvents ? VecHelper.toBlockPos(transaction.getOriginal().getPosition()) : null;
                final List<BlockEventData> events =  hasEvents ? scheduledEvents.get(pos) : Collections.emptyList();
                noCancelledTransactions = TrackingUtil.performTransactionProcess(transaction, phaseState, phaseContext, events, noCancelledTransactions, currentDepth);
            }
            return noCancelledTransactions;
        }
        Transaction<BlockSnapshot> eventTransaction = transactions.get(targetIndex);
        for (Map.Entry<BlockPos, EventTransaction> entry : this.orderedTransactions.entries()) {
            final EventTransaction transaction = entry.getValue();
            final SpongeBlockSnapshot oldSnapshot = transaction.changedSnapshot;

            if (transaction.snapshotIndex > targetIndex) {
                targetIndex++;
                eventTransaction = transactions.get(targetIndex);
            }
            if (oldSnapshot == null) {
                // Check for neighbor notifications?
                final NeighborNotification notification = transaction.notification;
                if (notification == null) {
                    continue;
                }
                // Otherwise, we have a neighbor notification to process.
                final IMixinWorldServer worldServer = notification.worldServer;
                final IBlockState sourceState = notification.source;
                final BlockPos notifyPos = notification.notifyPos;
                final Block sourceBlock = notification.sourceBlock;
                final BlockPos sourcePos = notification.sourcePos;
                PhaseTracker.getInstance().performNeighborNotificationOnTarget(worldServer, notifyPos, sourceBlock, sourcePos, sourceState);
                // And then we don't want to continue trying on null objects...
                // If there were tile entities being added/removed, whatever, we don't care about "processing" them, because they just add/remove
                // from the world/chunk and then possibly throw some extra neighbor notifications, which are caught.
                continue;
            }

            if (!eventTransaction.isValid()) {
                continue;
            }
            final SpongeBlockSnapshot newBlockSnapshot = (SpongeBlockSnapshot) eventTransaction.getFinal();
            final IBlockState newState = (IBlockState) newBlockSnapshot.getState();
            final WorldServer worldServer = oldSnapshot.getWorldServer();
            final BlockPos targetPosition = oldSnapshot.getBlockPos();
            TrackingUtil.performBlockEntitySpawns(phaseState, phaseContext, oldSnapshot, targetPosition);
            SpongeHooks.logBlockAction(worldServer, oldSnapshot.blockChange, eventTransaction);
            final IBlockState oldState = (IBlockState) oldSnapshot.getState();


            // We call onBlockAdded here for blocks without a TileEntity.
            // MixinChunk#setBlockState will call onBlockAdded for blocks
            // with a TileEntity or when capturing is not being done.
            final SpongeBlockChangeFlag changeFlag = oldSnapshot.getChangeFlag();
            TrackingUtil.performOnBlockAdded(phaseState, phaseContext, currentDepth, targetPosition, worldServer, changeFlag, oldState, newState);
            phaseState.postBlockTransactionApplication(oldSnapshot.blockChange, eventTransaction, phaseContext);

            phaseState.performPostBlockNotificationsAndNeighborUpdates(phaseContext, oldSnapshot, newState, changeFlag, currentDepth + 1);

            if (changeFlag.isNotifyClients()) { // Always try to notify clients of the change.
                worldServer.notifyBlockUpdate(targetPosition, oldState, newState, changeFlag.getRawFlag());
            }

            TrackingUtil.performNeighborAndClientNotifications(phaseContext, currentDepth, oldSnapshot, newBlockSnapshot,
                ((IMixinWorldServer) worldServer), targetPosition, oldState, newState, changeFlag);
        }
        return noCancelledTransactions;
    }
}
