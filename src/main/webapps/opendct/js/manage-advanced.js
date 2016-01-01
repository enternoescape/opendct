/*
Manage advanced content
=====================================
*/

var manageEditDevices = [];

function showManageAdvanced( requester ) {
    $("#manage-basic").hide();
    $("#manage-advanced-devices").empty();

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

    $("#manage-advanced").show();
}

function hideManageAdvanced() {
    $("#manage-advanced").hide();

    // This ensures that if the advanced options are not displayed, we can't accidentally apply
    // settings in some unexpected way.
    manageEditDevices = [];

    $("#manage-basic").show();
}