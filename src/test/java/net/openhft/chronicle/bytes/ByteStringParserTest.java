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

import org.junit.Assert;
import org.junit.Test;

public class ByteStringParserTest   {

    @Test
    public void testParseLong() {
        Bytes b = Bytes.elasticByteBuffer();
        long expected = 123L;
        b.append(expected);

        Assert.assertEquals(expected, BytesInternal.parseLong(b));
    }

    @Test
    public void testParseInt() {
        Bytes b = Bytes.elasticByteBuffer();
        int expected = 123;
        b.append(expected);

        Assert.assertEquals(expected, BytesInternal.parseLong(b));
    }

    @Test
    public void testParseDouble() {
        Bytes b = Bytes.elasticByteBuffer();
        double expected = 123.1234;
        b.append(expected);

        Assert.assertEquals(expected, BytesInternal.parseDouble(b), 0);
    }

    @Test
    public void testParseFloat() {
        Bytes b = Bytes.elasticByteBuffer();
        float expected = 123;
        b.append(expected);

        Assert.assertEquals(expected, BytesInternal.parseDouble(b), 0);
    }

    @Test
    public void testParseShort() {
        Bytes b = Bytes.elasticByteBuffer();
        short expected = 123;
        b.append(expected);

        Assert.assertEquals(expected, BytesInternal.parseLong(b));
    }
}