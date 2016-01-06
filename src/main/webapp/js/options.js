function getProducer( name, impl ) {
    switch (impl) {
        case "opendct.capture.RTPCaptureDevice":
            return getRTPProducerOptions( name );
            break;
        case "opendct.capture.HTTPCaptureDevice":
            return getHTTPProducerOptions( name );
            break;
        default:
            return "";
    }
}

function getRTPProducerOptions( name ) {
    return "<select class=\"form-control manage-producer-value manage-advanced-value\" name=\"" + name + "\">" +
              "<option value=\"opendct.producer.NIORTPProducerImpl\">NIO RTP</option>" +
           "</select>";
}

function getHTTPProducerOptions( name ) {
    return "<select class=\"form-control manage-producer-value manage-advanced-value\" name=\"" + name + "\">" +
              "<option value=\"opendct.producer.HTTPProducerImpl\">HTTP</option>" +
           "</select>";
}

function getConsumerOptions( name ) {
    return "<select class=\"form-control manage-consumer-value manage-advanced-value\" name=\"" + name + "\">" +
              "<option value=\"opendct.consumer.FFmpegSageTVConsumerImpl\">FFmpeg</option>" +
              "<option value=\"opendct.consumer.RawSageTVConsumerImpl\">Raw</option>" +
           "</select>";
}