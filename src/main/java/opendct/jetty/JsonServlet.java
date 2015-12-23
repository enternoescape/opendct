package opendct.jetty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import opendct.sagetv.SageTVManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

public class JsonServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonRequest jsonRequest[] = objectMapper.readValue(request.getReader(), JsonRequest[].class);

        ObjectReader objectReader = objectMapper.readerForUpdating(SageTVManager.class.getDeclaredClasses());
        objectReader.readValue(request.getReader());
    }

    private static void sageTVManager(BufferedReader reader, PrintWriter writer, String requestName, String parameters[]) {

    }

    private static void channelManager(BufferedReader reader, PrintWriter writer, String requestName, String parameters[]) {

    }
}
