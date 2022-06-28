package dev.vality.wachter.utils;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TMemoryInputTransport;
import org.apache.thrift.transport.TTransportException;

public class MethodNameReader {

    public static String getMethodName(byte[] thriftBody) throws TException {
        TProtocol protocol = createProtocol(thriftBody);
        TMessage message = protocol.readMessageBegin();
        protocol.readMessageEnd();
        return message.name;
    }

    private static TProtocol createProtocol(byte[] thriftBody) throws TTransportException {
        return new TBinaryProtocol.Factory().getProtocol(new TMemoryInputTransport(thriftBody));
    }

}
