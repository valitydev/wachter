package dev.vality.wachter.testutil;


import org.apache.thrift.TException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TMemoryBuffer;
import org.apache.thrift.transport.TTransportException;

import java.util.Arrays;

public class TMessageUtil {

    public static byte[] createTMessage(TProtocolFactory protocolFactory) throws TException {
        TMemoryBufferWithLength memoryBuffer = new TMemoryBufferWithLength(1024);
        TProtocol protocol = protocolFactory.getProtocol(memoryBuffer);
        protocol.writeMessageBegin(new TMessage("methodName", TMessageType.REPLY, 0));
        protocol.writeMessageEnd();
        return Arrays.copyOf(memoryBuffer.getArray(), memoryBuffer.length());
    }

    public static class TMemoryBufferWithLength extends TMemoryBuffer {
        private int actualLength = 0;

        public TMemoryBufferWithLength(int size) throws TTransportException {
            super(size);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);
            actualLength += len;
        }

        @Override
        public int length() {
            return actualLength;
        }
    }
}
