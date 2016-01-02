/*
Manage advanced content
=====================================
*/

var manageEditDevices = [];

function showManageAdvanced( requester ) {
    $("#manage-basic").hide();
    $("#manage-advanced-devices").empty();
    $("#manager-advanced-capture-devices-body").empty();

    // Select create a list of the devices we are going to edit.
    if ($(requester).parent().parent().find(".manage-loaded-checkbox:checked").length == 0) {
        manageEditDevices = $(requester).map( function() {
            return $(this).parent().parent().find(".manage-loaded-device-lookup").text();
        });
    } else {
        manageEditDevices = $(".manage-loaded-checkbox:checked").map( function() {
            return $(this).parent().parent().find(".manage-loaded-device-lookup").text();
        });
    }

    $.each(manageEditDevices, function(i, deviceName) {
        $("#manage-advanced-devices").append(deviceName);
        $("#manage-advanced-devices").append('<br/>');
    });

    displayManageAdvancedProperties();

    $("#manage-advanced").show();
}

function hideManageAdvanced() {
    $("#manage-advanced").hide();

    // This ensures that if the advanced options are not displayed, we can't accidentally apply
    // settings in some unexpected way.
    manageEditDevices = [];
    $("#manage-advanced-devices").empty();
    $("#manager-advanced-capture-devices-body").empty();

    $("#manage-basic").show();
}

function displayManageAdvancedProperties() {
    $.each(manageEditDevices, function(i, deviceName) {
        /*$.get("rest/capturedevice/" + deviceName + "/load", "", function(data, status, xhr) {


        });*/
        $.ajax({
            type: "GET",
            url: "rest/capturedevice/" + deviceName + "/details",
            contentType: "application/json",
            success: function(data, status, xhr) {
                displayManageAdvancedProperty(deviceName, data);
            },
            error : function(xhr, status, error) {
                alert("Error " + status + ". There was a problem while getting properties for " + deviceName + ". Check log for details.");
            }
        });
    });
}

function displayManageAdvancedProperty( deviceName, data) {
    console.log(data);

    for (var key in data) {
        switch(key) {
            case "childCaptureDevices":
            case "encoderDeviceType":
            case "encoderParentName":
            case "encoderName":
            case "lastChannel":
            case "canEncodeFilename":
            case "canEncodeUploadID":
            case "canSwitch":
            case "recordStart":
            case "recordedBytes":
            case "recordFilename":
            case "recordQuality":
            case "networkDevice":
            case "remoteAddress":
            case "localAddress":
            case "locked":
            case "producerBaseImpl":
                break;
            case "merit":
                manageAdvancedAutoCreateInput( "Merit", "This value is used to prioritize capture devices. Higher numbers mean a higher priority. This value is imported by SageTV the first time it detects this network encoder. This value is also used when capture device pooling is enabled.", key, data[key]);
                break;
            case "encoderPoolName":
                manageAdvancedAutoCreateInput( "Pool Name", "All capture devices sharing the same pool name will be in the same pool. This value is case sensitive. Pooling is is disabled by default. Enable pooling under Configuration.", key, data[key]);
                break;
            case "alwaysForceExternalUnlock":
                manageAdvancedAutoCreateInput( "Always Force Unlock", "When enabled, this will allow OpenDCT to override any active device lock held by another program. When pooling is enabled, capture devices locked by external applications are only overridden as a last resort and if this property is enabled.", key, data[key]);
                break;
            case "channelLineup":
                manageAdvancedAutoCreateInput( "Channel Lineup", "This is the lineup used to request channels when a channel cannot be directly determined by the channel requested by SageTV. An example would be when tuning in CleanQAM mode the channel number is translated via the selected lineup to a frequency and program.", key, data[key]);
                break;
            case "offlineScanEnabled":
                manageAdvancedAutoCreateInput( "Offline Scan", "This enables this capture device to participate in offline channel scanning. Offline channel scanning parameters are defined per lineup.", key, data[key]);
                break;
            case "producer":
                manageAdvancedAutoCreateInput( "Producer", "This is the implementation to be used to collect data from the source capture device.", key, data[key]);
                break;
            case "consumer":
                manageAdvancedAutoCreateInput( "Consumer", "This is the implementation to be used to stream data from the producer to SageTV via upload id or directly to a file.", key, data[key]);
                break;
            case "offlineConsumer":
                manageAdvancedAutoCreateInput( "Offline Consumer", "This is the implementation to be used to check if the capture device is able to tune and receive valid data when channel scanning.", key, data[key]);
                break;
            case "encoderPort":
                manageAdvancedAutoCreateInput( "Network Encoder Port", "This is the port used to communicate with SageTV.", key, data[key]);
                break;
            default:
                manageAdvancedAutoCreateInput( key, "", key, data[key]);
        }
    }
}

function manageAdvancedCreateIfNotExist( label, description, property ) {
    if ($("#manager-advanced-capture-devices-body").find("#manage-advanced-prop-" + property).length == 0) {
        $("#manager-advanced-capture-devices-body").append('<tr id="manage-advanced-prop-' + property + '" class="no-change"><td class="manage-advanced-label">' + label + '</td><td class="manage-advanced-object"></td><td class="manage-advanced-description">' + description + '</td></tr>');
        return true;
    } else {
        return false;
    }
}

function manageAdvancedAutoCreateInput( label, description, property, value ) {
    if (manageAdvancedCreateIfNotExist( label, description, property)) {
        if (typeof value === 'number') {
            $('#manage-advanced-prop-' + property).find(".manage-advanced-object").html('<input type="number" min="0" max="2147483647" class="form-control manage-advanced-value" value="' + value + '">');
        } else if (typeof value === 'boolean') {
            $('#manage-advanced-prop-' + property).find(".manage-advanced-object").html('<input type="checkbox" class="manage-advanced-value">');
            $('#manage-advanced-prop-' + property).find(".manage-advanced-value").prop("checked", value);
        } else {
            $('#manage-advanced-prop-' + property).find(".manage-advanced-object").html('<input type="text" class="form-control manage-advanced-value" value="' + value + '">');
        }

    } else {
        var currentValue = $('#manage-advanced-prop-' + label).find(".manage-advanced-value").val();

        if (currentValue != value) {
            $('#manage-advanced-prop-' + label).find(".manage-advanced-value").val("");
        }
    }
}