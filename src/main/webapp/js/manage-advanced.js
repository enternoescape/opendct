/*
Manage advanced content
=====================================
*/

var manageEditDevices = [];

function showManageAdvanced( requester ) {
    $("#manage-basic").hide();
    $("#manage-advanced-devices").empty();
    $("#manager-advanced-capture-devices-body").empty();
    $("#manage-advanced-apply-changes").addClass("disabled");

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
    $("#manage-advanced-apply-changes").addClass("disabled");

    $("#manage-basic").show();
}

function displayManageAdvancedProperties() {
    $("#manage-advanced-device-warning").empty();

    $.each(manageEditDevices, function(i, deviceName) {
        $.ajax({
            type: "GET",
            url: "rest/capturedevice/" + deviceName + "/details",
            contentType: "application/json",
            success: function(data, status, xhr) {
                displayManageAdvancedProperty(deviceName, data);
            },
            error : function(xhr, status, error) {
                alert("Error '" + status + "'. There was a problem while getting properties for '" + deviceName + "'. Check log for details.");
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
                break;
            case "producerBaseImpl":
                if (manageAdvancedCreateIfNotExist("Producer", "", "producer")) {
                    console.log("Pre");
                    $("#manage-advanced-prop-producer").find(".manage-advanced-object").html( getProducer( key, data[key] ) );
                } else if ($("#manager-advanced-capture-devices-body").find(".manage-producer-value").length == 0) {
                    console.log("Aft");
                    // If the producer has already populated itself as a text box, this will convert it to a dropdown.
                    var originalValue = $("#manage-advanced-prop-producer").find(".manage-advanced-value").val();
                    $("#manage-advanced-prop-producer").find(".manage-advanced-object").html( getProducer( key, data[key] ) );
                    $("#manage-advanced-prop-producer").find(".manage-advanced-value").val(originalValue);
                }
                break;
            case "merit":
                manageAdvancedAutoCreateInput( "Merit", "This value is used to prioritize capture devices. Higher numbers mean a higher priority. This value is imported by SageTV the first time it detects this network encoder. This value is also used when capture device pooling is enabled.", key, data[key]);
                break;
            case "encoderPoolName":
                manageAdvancedAutoCreateInput( "Pool Name", "All capture devices sharing the same pool name will be in the same pool. This value is case sensitive. Pooling is is disabled by default. Enable pooling under Configuration.", key, data[key]);
                if ( data.locked ) {
                    // You can't change the pool if the device is in use since that might cause
                    // problems so we will hide this value
                    $('#manage-advanced-prop-' + key).find(".manage-advanced-value").addClass("hidden");
                }
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
                manageAdvancedCreateOption( "Consumer", "This is the implementation to be used to stream data from the producer to SageTV via upload id or directly to a file.", key, data[key], getConsumerOptions("manage-consumer-value"));
                break;
            case "offlineConsumer":
                manageAdvancedCreateOption( "Offline Consumer", "This is the implementation to be used to check if the capture device is able to tune and receive valid data when channel scanning.", key, data[key], getConsumerOptions("manage-consumer-value"));
                break;
            case "encoderPort":
                manageAdvancedAutoCreateInput( "Network Encoder Port", "This is the port used to communicate with SageTV.", key, data[key]);
                break;
            default:
                manageAdvancedAutoCreateInput( key, "", key, data[key]);
        }
    }

    $("#manager-advanced-capture-devices-body").find('.manage-advanced-value').on("change", function() {
        $("#manage-advanced-apply-changes").removeClass("disabled");
        $(this).parent().addClass("apply-setting");
    });
}

function manageAdvancedCreateIfNotExist( label, description, property ) {
    if ($("#manager-advanced-capture-devices-body").find("#manage-advanced-prop-" + property).length == 0) {
        $("#manager-advanced-capture-devices-body").append('<tr id="manage-advanced-prop-' + property + '"><td class="manage-advanced-label">' + label + '</td><td class="manage-advanced-object"></td><td class="manage-advanced-description">' + description + '</td></tr>');

        $('manage-advanced-prop-' + property).on("change", function() {
            $("#manager-advanced-capture-devices-body").find("#manage-advanced-prop-" + property).addClass("manager-advanced-apply-change");
        });
        return true;
    } else {
        return false;
    }
}

function manageAdvancedAutoCreateInput( label, description, property, value ) {
    if (manageAdvancedCreateIfNotExist( label, description, property)) {
        if (typeof value === "number") {
            $("#manage-advanced-prop-" + property).find(".manage-advanced-object").html("<input type=\"number\" min=\"0\" max=\"2147483647\" class=\"form-control manage-advanced-value\" value=\"" + value + "\">");
        } else if (typeof value === "boolean") {
            $("#manage-advanced-prop-" + property).find(".manage-advanced-object").html("<input type=\"checkbox\" class=\"checkbox form-control manage-advanced-value\">");
            $("#manage-advanced-prop-" + property).find(".manage-advanced-value").prop("checked", value);
        } else {
            $("#manage-advanced-prop-" + property).find(".manage-advanced-object").html("<input type=\"text\" class=\"form-control manage-advanced-value\" value=\"" + value + "\">");
        }
    } else {
        var domProperty = $("#manage-advanced-prop-" + property).find(".manage-advanced-value");

        if (domProperty.attr("type") == "checkbox") {
            var currentValue = $("#manage-advanced-prop-" + property).find(".manage-advanced-value").prop("checked");

            if (currentValue != value) {
                manageDisplayAdvancedConflictMessage( property );
                $("#manage-advanced-prop-" + property).find(".manage-advanced-value").prop("checked", false);
            }
        } else {
            var currentValue = $("#manage-advanced-prop-" + property).find(".manage-advanced-value").val();

            if (currentValue != value) {
                manageDisplayAdvancedConflictMessage( property );
                $("#manage-advanced-prop-" + property).find(".manage-advanced-value").val("");
            }
        }
    }
}

function manageAdvancedCreateOption( label, description, property, value, object ) {
    if (manageAdvancedCreateIfNotExist( label, description, property)) {
        $("#manage-advanced-prop-" + property).find(".manage-advanced-object").html(object);
        $("#manage-advanced-prop-" + property).find(".manage-advanced-value").val(value);
    } else {
        var currentValue = $("#manage-advanced-prop-" + property).find(".manage-advanced-value").val();

        if (currentValue != value) {
            manageDisplayAdvancedConflictMessage( property );
            $("#manage-advanced-prop-" + property).find(".manage-advanced-value").val("");
        }
    }
}

function manageDisplayAdvancedConflictMessage( value ) {
    $("#manage-advanced-device-warning").html("Multiple capture devices have been selected that have conflicting properties. The conflicts have been highlighted in yellow. By changing any highlighted properties, the new value for that property will be applied to all currently selected capture devices that are listed above.");
    $("#manage-advanced-prop-" + value).find(".manage-advanced-object").addClass("has-warning");
    $("#manage-advanced-prop-" + value).find(".manage-advanced-label").addClass("text-conflict");
    //text-conflict
    console.log(value);
}

$("#manage-advanced-undo-changes").on("click", function() {
    $("#manager-advanced-capture-devices-body").empty();
    displayManageAdvancedProperties();
});

$("#manage-advanced-apply-changes").on("click", function() {
    if ($(this).hasClass("disabled")) {
        return;
    }

    if (!confirm("Are you sure you want to apply these changes?")) {
        return;
    }

    $("#manage-advanced-apply-changes").addClass("disabled");

    $.each(($("#manager-advanced-capture-devices-body").find(".apply-setting")), function(i, row) {
        console.log( $(this).find(".manage-advanced-value").val() );
    });

    $("#manager-advanced-capture-devices-body").empty();
    displayManageAdvancedProperties();
});
