package dev.vality.wachter.service;

import lombok.RequiredArgsConstructor;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TMemoryInputTransport;
import org.apache.thrift.transport.TTransportException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MethodNameReaderService {

    private final TProtocolFactory thriftProtocolFactory;

    public String getMethodName(byte[] thriftBody) throws TException {
        TProtocol protocol = createProtocol(thriftBody);
        TMessage message = protocol.readMessageBegin();
        protocol.readMessageEnd();
        return message.name;
    }

    private TProtocol createProtocol(byte[] thriftBody) throws TTransportException {
        return thriftProtocolFactory.getProtocol(new TMemoryInputTransport(thriftBody));
    }
}
