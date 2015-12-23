package opendct.jetty;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class Main extends HttpServlet {
    private static final Logger logger = LogManager.getLogger(Main.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        PrintWriter out = response.getWriter();
        WebDivBuilders.printHeader(out);
        WebDivBuilders.printLoadedCaptureDeviceTable(out);

        WebDivBuilders.printFooter(out);
    }
}
