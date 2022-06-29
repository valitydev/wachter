package dev.vality.wachter.util;

import dev.vality.wachter.testutil.TMessageUtil;
import dev.vality.wachter.utils.MethodNameReader;
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

    @Test
    void readMethodName() throws TException {
        byte[] message = TMessageUtil.createTMessage(protocolFactory);
        assertEquals("methodName", MethodNameReader.getMethodName(message));
    }

}
