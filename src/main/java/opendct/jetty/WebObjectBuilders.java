package opendct.jetty;

import opendct.capture.CaptureDevice;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVPoolManager;

public class WebObjectBuilders {

    public static String getCaptureDeviceDetails(String captureDeviceName) {
        CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(captureDeviceName, false);

        if (captureDevice == null) {
            return "";
        }

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("<div id=\"captureDeviceDetails\">");

        stringBuilder.append(captureDevice.getEncoderName()).append("<p/>");

        String poolName = captureDevice.getEncoderPoolName();

        if (poolName != null && !poolName.equals("") && SageTVPoolManager.isUsePools()) {
            stringBuilder.append("Pool: ").append(captureDevice.getEncoderPoolName()).append("<p/>");
            stringBuilder.append("Merit: ").append(captureDevice.getMerit()).append("<p/>");
        }

        if (captureDevice.isLocked()) {
            stringBuilder.append("Recording: ").append(captureDevice.getRecordFilename()).append("<p/>");
            stringBuilder.append("Channel: ").append(captureDevice.getLastChannel()).append("<p/>");
        }

        stringBuilder.append("</div>");
        return stringBuilder.toString();
    }
}
