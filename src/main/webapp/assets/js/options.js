function getRTPProducerOptions(name) {
    "use strict";
    
    return "<select class=\"form-control manage-producer-value manage-advanced-value\" name=\"" + name + "\">" +
              "<option value=\"opendct.producer.NIORTPProducerImpl\">NIO RTP</option>" +
           "</select>";
}

function getHTTPProducerOptions(name) {
    "use strict";
    
    return "<select class=\"form-control manage-producer-value manage-advanced-value\" name=\"" + name + "\">" +
              "<option value=\"opendct.producer.HTTPProducerImpl\">HTTP</option>" +
           "</select>";
}

function getProducer(name, impl) {
    "use strict";
    
    switch (impl) {
    case "opendct.capture.RTPCaptureDevice":
        return getRTPProducerOptions(name);
    case "opendct.capture.HTTPCaptureDevice":
        return getHTTPProducerOptions(name);
    default:
        return "";
    }
}

function getConsumerOptions(name) {
    "use strict";
    
    return "<select class=\"form-control manage-consumer-value manage-advanced-value\" name=\"" + name + "\">" +
              "<option value=\"opendct.consumer.FFmpegSageTVConsumerImpl\">FFmpeg</option>" +
              "<option value=\"opendct.consumer.RawSageTVConsumerImpl\">Raw</option>" +
           "</select>";
}