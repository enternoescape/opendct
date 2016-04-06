package opendct;

import opendct.video.http.NIOHttpDownloader;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;

public class NioHttp {

    //@Test(groups = { "http", "NIO" })
    public void testConnect() throws URISyntaxException, IOException {

        NIOHttpDownloader downloader = new NIOHttpDownloader();

        downloader.connect(new URL("http://192.168.1.109:5004/auto/v8.2?timeout=120"));

        ByteBuffer dataOut = ByteBuffer.allocate(1048576);
        dataOut.clear();

        downloader.read(dataOut);
        dataOut.flip();


    }
}
