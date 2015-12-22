package opendct.jetty;

import opendct.capture.CaptureDevice;
import opendct.sagetv.SageTVManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class GetSetSageTVManager extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);

        for (CaptureDevice captureDevice : SageTVManager.getAllSageTVCaptureDevices()) {
            response.getWriter().println(captureDevice.getEncoderName() + "<p/>");
        }

        // Get the property.
        String property = request.getParameter("p");
        if (property == null) {
            return;
        }

        // Get the value to set.
        String value = request.getParameter("v");
        boolean setValue = !(value == null);

        if (property.equals("")) {

        }
    }
}
