/*
 *     Copyright (C) 2015  higherfrequencytrading.com
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.bytes;

import net.openhft.chronicle.core.Maths;
import net.openhft.chronicle.core.Memory;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.ReferenceCounter;
import net.openhft.chronicle.core.annotation.ForceInline;
import org.jetbrains.annotations.Nullable;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.nio.ByteBuffer;

public class NativeBytesStore<Underlying>
        implements BytesStore<NativeBytesStore<Underlying>, Underlying> {
    private static final long MEMORY_MAPPED_SIZE = 128 << 10;
    @Nullable
    private final Cleaner cleaner;
    private final ReferenceCounter refCount = ReferenceCounter.onReleased(this::performRelease);
    private final boolean elastic;
    private final Underlying underlyingObject;
    // on release, set this to null.
    protected Memory memory = OS.memory();
    protected long address;
    long maximumLimit;

    private NativeBytesStore(ByteBuffer bb, boolean elastic) {
        this.elastic = elastic;
        underlyingObject = (Underlying) bb;
        setAddress(((DirectBuffer) bb).address());
        this.maximumLimit = bb.capacity();
        cleaner = ((DirectBuffer) bb).cleaner();
    }

    public NativeBytesStore(
            long address, long maximumLimit, Runnable deallocator, boolean elastic) {
        setAddress(address);
        this.maximumLimit = maximumLimit;
        cleaner = deallocator == null ? null : Cleaner.create(this, deallocator);
        underlyingObject = null;
        this.elastic = elastic;
    }

    public static NativeBytesStore<ByteBuffer> wrap(ByteBuffer bb) {
        return new NativeBytesStore<>(bb, false);
    }

    /**
     * this is an elastic native store
     *
     * @param capacity of the buffer.
     */
    public static NativeBytesStore<Void> nativeStore(long capacity) {
        return of(capacity, true, true);
    }

    private static NativeBytesStore<Void> of(long capacity, boolean zeroOut, boolean elastic) {
        Memory memory = OS.memory();
        long address = memory.allocate(capacity);
        if (zeroOut || capacity < MEMORY_MAPPED_SIZE) {
            memory.setMemory(address, capacity, (byte) 0);
            memory.storeFence();
        }
        Deallocator deallocator = new Deallocator(address);
        return new NativeBytesStore<>(address, capacity, deallocator, elastic);
    }

    public static NativeBytesStore<Void> nativeStoreWithFixedCapacity(long capacity) {
        return of(capacity, true, false);
    }

    public static NativeBytesStore<Void> lazyNativeBytesStoreWithFixedCapacity(long capacity) {
        return of(capacity, false, false);
    }

    public static NativeBytesStore<ByteBuffer> elasticByteBuffer() {
        return elasticByteBuffer(OS.pageSize());
    }

    public static NativeBytesStore<ByteBuffer> elasticByteBuffer(int size) {
        return new NativeBytesStore<>(ByteBuffer.allocateDirect(size), true);
    }

    @Override
    public BytesStore<NativeBytesStore<Underlying>, Underlying> copy() {
        if (underlyingObject == null) {
            NativeBytesStore<Void> copy = of(realCapacity(), false, true);
            OS.memory().copyMemory(address, copy.address, capacity());
            return (BytesStore) copy;

        } else if (underlyingObject instanceof ByteBuffer) {
            ByteBuffer bb = ByteBuffer.allocateDirect(Maths.toInt32(capacity()));
            bb.put((ByteBuffer) underlyingObject);
            bb.clear();
            return (BytesStore) wrap(bb);

        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Bytes<Underlying> bytesForWrite() {
        return elastic ? new NativeBytes<>(this) : new VanillaBytes<>(this);
    }

    @Override
    @ForceInline
    public long realCapacity() {
        return maximumLimit;
    }

    @Override
    @ForceInline
    public long capacity() {
        return maximumLimit;
    }

    @Override
    @ForceInline
    public Underlying underlyingObject() {
        return underlyingObject;
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> zeroOut(long start, long end) {
        if (start < writePosition() || end > writeLimit())
            throw new IllegalArgumentException("position: " + writePosition() + ", start: " + start + ", end: " + end + ", limit: " + writeLimit());
        if (start >= end)
            return this;

        memory.setMemory(address + translate(start), end - start, (byte) 0);
        return this;
    }

    @Override
    @ForceInline
    public boolean compareAndSwapInt(long offset, int expected, int value) {
        return memory.compareAndSwapInt(address + translate(offset), expected, value);
    }

    @Override
    @ForceInline
    public boolean compareAndSwapLong(long offset, long expected, long value) {
        return memory.compareAndSwapLong(address + translate(offset), expected, value);
    }

    long translate(long offset) {
        long offset2 = offset - start();
        assert checkTranslatedBounds(offset2);
        return offset2;
    }

    private boolean checkTranslatedBounds(long offset2) {
        if (offset2 < 0 || offset2 > realCapacity())
            throw new IllegalArgumentException("Offset out of bounds " + offset2 + " cap: " + realCapacity());
        return true;
    }

    @Override
    public void reserve() {
        refCount.reserve();
    }

    //    Error releasedHere;
    @Override
    public void release() {
        refCount.release();
//        if (Jvm.isDebug() && refCount.get() == 0)
//            releasedHere = new Error();
    }

    @Override
    public long refCount() {
        return refCount.get();
    }

    @Override
    @ForceInline
    public byte readByte(long offset) {
//        if (Jvm.isDebug()) checkReleased();

        return memory.readByte(address + translate(offset));
    }

//    public void checkReleased() {
//        if (releasedHere != null)
//            throw new InternalError("Accessing a released resource", releasedHere);
//    }

    @Override
    @ForceInline
    public short readShort(long offset) {
        return memory.readShort(address + translate(offset));
    }

    @Override
    @ForceInline
    public int readInt(long offset) {
        return memory.readInt(address + translate(offset));
    }

    @Override
    @ForceInline
    public long readLong(long offset) {
        return memory.readLong(address + translate(offset));
    }

    @Override
    @ForceInline
    public float readFloat(long offset) {
        return memory.readFloat(address + translate(offset));
    }

    @Override
    @ForceInline
    public double readDouble(long offset) {
        return memory.readDouble(address + translate(offset));
    }

    @Override
    @ForceInline
    public int readVolatileInt(long offset) {
        return memory.readVolatileInt(address + translate(offset));
    }

    @Override
    @ForceInline
    public long readVolatileLong(long offset) {
        return memory.readVolatileLong(address + translate(offset));
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> writeByte(long offset, byte i8) {
        memory.writeByte(address + translate(offset), i8);
        return this;
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> writeShort(long offset, short i16) {
        memory.writeShort(address + translate(offset), i16);
        return this;
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> writeInt(long offset, int i32) {
        memory.writeInt(address + translate(offset), i32);
        return this;
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> writeOrderedInt(long offset, int i) {
        memory.writeOrderedInt(address + translate(offset), i);
        return this;
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> writeLong(long offset, long i64) {
        memory.writeLong(address + translate(offset), i64);
        return this;
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> writeOrderedLong(long offset, long i) {
        memory.writeOrderedLong(address + translate(offset), i);
        return this;
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> writeFloat(long offset, float f) {
        memory.writeFloat(address + translate(offset), f);
        return this;
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> writeDouble(long offset, double d) {
        memory.writeDouble(address + translate(offset), d);
        return this;
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> write(
            long offsetInRDO, byte[] bytes, int offset, int length) {
        memory.copyMemory(bytes, offset, address + translate(offsetInRDO), length);
        return this;
    }

    @Override
    @ForceInline
    public void write(
            long offsetInRDO, ByteBuffer bytes, int offset, int length) {
        if (bytes.isDirect()) {
            memory.copyMemory(((DirectBuffer) bytes).address(),
                    address + translate(offsetInRDO), length);

        } else {
            memory.copyMemory(bytes.array(), offset, address + translate(offsetInRDO), length);
        }
    }

    @Override
    @ForceInline
    public NativeBytesStore<Underlying> write(
            long offsetInRDO, RandomDataInput bytes, long offset, long length) {
        // TODO optimize, call unsafe.copyMemory when possible, copy 4, 2 bytes at once
        long i = 0;
        for (; i < length - 7; i += 8) {
            writeLong(offsetInRDO + i, bytes.readLong(offset + i));
        }
        for (; i < length; i++) {
            writeByte(offsetInRDO + i, bytes.readByte(offset + i));
        }
        return this;
    }

    @Override
    public long address(long offset) throws UnsupportedOperationException {
        return address + translate(offset);
    }

    private void performRelease() {
        memory = null;
        if (cleaner != null)
            cleaner.clean();
    }

    @Override
    public String toString() {
        return BytesUtil.toString(this);
    }

    @Override
    @ForceInline
    public void nativeRead(long position, long address, long size) {
        // TODO add bounds checking.
        OS.memory().copyMemory(address(position), address, size);
    }

    @Override
    @ForceInline
    public void nativeWrite(long address, long position, long size) {
        // TODO add bounds checking.
        OS.memory().copyMemory(address, address(position), size);
    }

    void write8bit(long position, char[] chars, int offset, int length) {
        long addr = address + translate(position);
        Memory memory = OS.memory();
        for (int i = 0; i < length; i++)
            memory.writeByte(addr + i, (byte) chars[offset + i]);
    }

    void read8bit(long position, char[] chars, int length) {
        long addr = address + translate(position);
        Memory memory = OS.memory();
        for (int i = 0; i < length; i++)
            chars[i] = (char) (memory.readByte(addr + i) & 0xFF);
    }

    public long readIncompleteLong(long offset) {
        int remaining = (int) Math.min(8, readRemaining() - offset);
        long l = 0;
        for (int i = 0; i < remaining; i++) {
            byte b = memory.readByte(address + offset + i);
            l |= (long) (b & 0xFF) << (i * 8);
        }
        return l;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BytesStore && BytesUtil.contentEqual(this, (BytesStore) obj);
    }

    public void setAddress(long address) {
        if ((address & ~0x3FFF) == 0)
            throw new AssertionError("Invalid address " + Long.toHexString(address));
        this.address = address;
    }

    static class Deallocator implements Runnable {
        private volatile long address;

        Deallocator(long address) {
            assert address != 0;
            this.address = address;
        }

        @Override
        public void run() {
            if (address == 0)
                return;
            address = 0;
            OS.memory().freeMemory(address);
        }
    }
}