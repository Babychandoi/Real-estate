package com.company.bds.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Component
public class ClamAvScanner {
    private final boolean enabled; private final String host; private final int port; private final int timeout;
    public ClamAvScanner(@Value("${app.media.antivirus.enabled:false}") boolean enabled,
                         @Value("${app.media.antivirus.host:localhost}") String host,
                         @Value("${app.media.antivirus.port:3310}") int port,
                         @Value("${app.media.antivirus.timeout-ms:15000}") int timeout) {
        this.enabled=enabled;this.host=host;this.port=port;this.timeout=timeout;
    }
    public void assertClean(byte[] bytes) {
        if(!enabled) return;
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress(host,port),timeout); socket.setSoTimeout(timeout);
            OutputStream out=socket.getOutputStream(); out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for(int offset=0;offset<bytes.length;offset+=8192){int length=Math.min(8192,bytes.length-offset);out.write(ByteBuffer.allocate(4).putInt(length).array());out.write(bytes,offset,length);}
            out.write(new byte[4]);out.flush();
            String response=new String(socket.getInputStream().readNBytes(4096),StandardCharsets.UTF_8).trim();
            if(response.contains("FOUND")) throw new IllegalArgumentException("Tệp bị từ chối vì phát hiện mã độc.");
            if(!response.endsWith("OK") && !response.contains("OK\0")) throw new IllegalStateException("ClamAV không trả kết quả hợp lệ: "+response);
        } catch(IllegalArgumentException ex){throw ex;} catch(Exception ex){throw new IllegalStateException("Không thể xác minh an toàn tệp; hệ thống từ chối tải lên.",ex);}
    }
}
