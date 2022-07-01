package dev.vality.wachter.util;

import dev.vality.wachter.service.MethodNameReaderService;
import dev.vality.wachter.testutil.TMessageUtil;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class MethodNameReaderTest {

    @Autowired
    private TProtocolFactory protocolFactory;

    @Autowired
    private MethodNameReaderService methodNameReaderService;

    @Test
    void readMethodName() throws TException {
        byte[] message = TMessageUtil.createTMessage(protocolFactory);
        assertEquals("methodName", methodNameReaderService.getMethodName(message));
    }
}
