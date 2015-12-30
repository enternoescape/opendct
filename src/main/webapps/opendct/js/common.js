$(document).ready(function() {
    // Takes us to the dashboard by default page or returns us to the last page.
    if (window.location.hash != '') {
        $(window.location.hash + "-nav").addClass("active");
        $(window.location.hash + "-page").show();
        loadPage( window.location.hash );
    } else {
        $("#dashboard-nav").addClass("active");
        $("#dashboard-page").show();
        loadPage( "#dashboard" );
    }
});

/*
Navigation Bar
=====================================
*/
$(".nav a").on("click", function(){
   $(this).parent().parent().find(".active").removeClass('active');
   $(this).parent().addClass("active");
});

$(".navbar-header a").on("click", function(){
    $(".navbar").find(".nav").find(".active").removeClass('active');
    $("#dashboard-nav").addClass("active");
});

$(".change-page").on("click", function() {
    $(".content-page").hide();
    $($(this).attr('href') + "-page").show();

    loadPage( $(this).attr('href') );
});

function changePage( page ) {
    document.location.hash = page
    $(".content-page").hide();
    $(page + "-page").show();

    $(".navbar").find(".nav").find(".active").removeClass('active');
    $(page + "-nav").addClass("active");

    loadPage( page );
}

function loadPage( page ) {
    if (page == "#manage") {
        createManageParentRows();
        createManageUnloadedRows();
    } else if (page == "#lineups") {

    } else if (page == "#pools") {

    } else if (page == "#config") {

    } else {
        createDashboardRows();
    }
}

function updatePage( page ) {
    if (page == "#manage") {
        updateManageParentRows();
    } else if (page == "#lineups") {

    } else if (page == "#pools") {

    } else if (page == "#config") {

    } else {
        updateDashboard();
    }
}

/*
Dashboard content
=====================================
*/

var dashContent = $("#dashboard-content");
var deviceTable = $("#dashboard-capture-devices-body");
var poolEnabled = false;

function createDashboardRows() {
    $.get("rest/capturedevice", "", function(data, status, xhr) {
        if (data.length == 0) {
            $("#dashboard-capture-devices-header").hide();
            deviceTable.empty();
            deviceTable.append('<tr><td><button class="btn btn-primary" onclick="changePage(\'#manage\');">Load Capture Devices</button></td>' +
                                                   "<td class=\"dashboard-status\"></td>" +
                                                   "<td class=\"dashboard-locked\"></td>" +
                                                   "<td class=\"dashboard-lineup\"></td>" +
                                                   "<td class=\"dashboard-pool\"></td></tr>");
        } else {
            $("#dashboard-capture-devices-header").show();
            deviceTable.empty();
            $.each(data, function(i, deviceName) {
                deviceTable.append('<tr><td class="dashboard-device-name">' +
                                       '<a href="#" title="Click to expand/collapse." data-toggle="collapse" data-target="#dashboard-device-collapse-' + i + '">' +
                                       '<div class="dashboard-device-lookup">' + deviceName + '</div></a>' +
                                       '<div class="dashboard-collapse collapse" id="dashboard-device-collapse-' + i + '">Updating...</div></td>' +
                                       "<td class=\"dashboard-status\"></td>" +
                                       "<td class=\"dashboard-locked\"></td>" +
                                       "<td class=\"dashboard-lineup\"></td>" +
                                       "<td class=\"dashboard-pool\"></td></tr>");
            });
        }

        $.get("rest/pool", "", function(data, status, xhr) {
            poolEnabled = data;

            if (data) {
                $(".dashboard-pool").show();
                $(".dashboard-pool-header").show();
                $(".dashboard-pool-footer").show();
            } else {
                $(".dashboard-pool").hide();
                $(".dashboard-pool-header").hide();
                $(".dashboard-pool-footer").hide();
            }
        });

        updateDashboard();
    }, "json");
}


function updateDashboard() {
    $.each(deviceTable.find("div.dashboard-device-lookup"), function(i, deviceName) {
        $.get("rest/capturedevice/" + $(deviceName).text() + "/details", "", function(data, status, xhr) {
            var deviceDiv = $(deviceName).parent().parent().find(".dashboard-collapse");
            if (data.locked == true) {
                deviceDiv.html('<br/>Recording: ' + data.recordFilename);
                deviceDiv.append('<br/>Channel: ' + data.lastChannel);
            } else {
                deviceDiv.html('<p/>There is no activity on this capture device.');
            }

            var statusDiv = $(deviceName).parent().parent().parent().find(".dashboard-status");
            statusDiv.html('<a href="#" title="Click to expand/collapse." data-toggle="collapse" data-target="#dashboard-status-' + i + '">' + (data.locked ? "Active" : "Idle") + '</a>');
            if (data.locked == true) {
                statusDiv.append('<div class="dashboard-collapse collapse" id="dashboard-status-' + i + '"><br/><span class="signal"></span><br/><span class="cci"></span></div>');

                $.get("rest/capturedevice/" + $(deviceName).text() + "/method/getSignalStrength", "", function(data, status, xhr) {
                    statusDiv.find(".signal").html("Signal: " + data);
                });

                $.get("rest/capturedevice/" + $(deviceName).text() + "/method/getCopyProtection", "", function(data, status, xhr) {
                    statusDiv.find(".cci").html("CCI: " + data);
                });
            } else {
                statusDiv.append('<div class="dashboard-collapse collapse" id="dashboard-status-' + i + '"><p/></div>');
            }

            $(deviceName).parent().parent().parent().find(".dashboard-lineup").html(data.channelLineup);

            if (poolEnabled == true) {
                $(deviceName).parent().parent().parent().find(".dashboard-pool").html(data.encoderPoolName);
                if (data.locked == true) {
                    $.get("rest/pool/" + $(deviceName).text() + "/tovirtualdevice", "", function(data, status, xhr) {
                        if (status == "success") {
                            deviceDiv.append('<br/>SageTV Virtual Device: ' + data);
                        } else {
                            deviceDiv.append('<br/>SageTV Virtual Device: Not Mapped');
                        }
                    });
                }
            }
        });

        // We are trying to avoid directly accessing the devices as much as possible, but there is
        // no other way to know if the device is locked or not.
        $.get("rest/capturedevice/" + $(deviceName).text() + "/method/isExternalLocked", "", function(data, status, xhr) {
            if ($(deviceName).parent().parent().parent().find(".dashboard-status").text() == "Active") {
                $(deviceName).parent().parent().parent().find(".dashboard-locked").html(data.locked ? "External" : "SageTV");
            } else {
                $(deviceName).parent().parent().parent().find(".dashboard-locked").html(data.locked ? "Locked" : "Available");
            }
        });
    });
}


/*
Manage content
=====================================
*/

var manageLoadedParentTable = $("#manager-loaded-parent-devices-body");
var manageLoadedTable = $("#manager-loaded-devices-body");
var manageUnloadedTable = $("#manager-unloaded-capture-devices-body");


function createManageParentRows() {
    $.get("rest/capturedeviceparent", "", function(data, status, xhr) {

    if (data.length == 0) {
        $("#manage-capture-devices-header").hide();
        manageLoadedParentTable.empty();
        manageLoadedParentTable.append(
            '<tr><td class=\"manage-parent-name\">There are no capture devices currently loaded.</td>' +
            "<td class=\"manage-is-network\"></td>" +
            "<td class=\"manage-remote-ip\"></td>" +
            "<td class=\"manage-local-ip\"></td>" +
            "<td class=\"manage-cablecard-present\"></td></tr>");
        } else {
            $("#dashboard-capture-devices-header").show();
            manageLoadedParentTable.empty();
            $.each(data, function(i, parentName) {
                manageLoadedParentTable.append(
                    '<tr><td class="manage-parent-name">' +
                    '<a href="#" title="Click to show loaded child capture device names." data-toggle="collapse" data-target="#manage-parent-name-collapse-' + i + '">' +
                    '<div class="manage-parent-lookup">' + parentName + '</div></a>' +
                    '<div class="manage-parent-collapse collapse" id="manage-parent-name-collapse-' + i + '">Updating...</div></td>' +
                    "<td class=\"manage-is-network\"></td>" +
                    "<td class=\"manage-remote-ip\"></td>" +
                    "<td class=\"manage-local-ip\"></td>" +
                    "<td class=\"manage-cablecard-present\"></td></tr>");
            });
        }

        updateManageParentRows();
    }, "json");
}

function updateManageParentRows() {
    $.each(manageLoadedParentTable.find("div.manage-parent-lookup"), function(i, parentName) {
        $.get("rest/capturedeviceparent/" + $(parentName).text() + "/details", "", function(data, status, xhr) {
            var parentDiv = $(parentName).parent().parent().find(".manage-parent-collapse");
            parentDiv.empty();

            $.each(data.encoderNames, function(i, childName) {
                parentDiv.append(childName + "<br/>");
            });

            var isNetworkDiv = $(parentName).parent().parent().parent().find(".manage-is-network");
            isNetworkDiv.html((data.networkDevice ? "Yes" : "No"));

            var remoteAddressDiv = $(parentName).parent().parent().parent().find(".manage-remote-ip");
            var localAddressDiv = $(parentName).parent().parent().parent().find(".manage-local-ip");
            if (data.networkDevice == true) {
                remoteAddressDiv.html(data.remoteAddress);
                localAddressDiv.html(data.localAddress);
            } else {
                remoteAddressDiv.html("N/A");
                localAddressDiv.html("N/A");
            }

            var isCablecardDiv = $(parentName).parent().parent().parent().find(".manage-cablecard-present");
            isCablecardDiv.html((data.cableCardPresent ? "Yes" : "No"));
        });
    });
}

function createManageLoadedRows() {
    $.get("rest/capturedeviceparent", "", function(data, status, xhr) {
        if (data.length == 0) {
            $("#manage-capture-devices-header").hide();
            deviceTable.empty();
            deviceTable.append('<tr><td class=\"manage-loaded-checked\">&nbsp;</td>' +
                                   "<td class=\"manage-loaded-name\">There are no capture devices currently loaded.</td>" +
                                   "<td class=\"dashboard-status\"></td>" +
                                   "<td class=\"dashboard-locked\"></td>" +
                                   "<td class=\"dashboard-lineup\"></td>" +
                                   "<td class=\"dashboard-pool\"></td></tr>");
        } else {
            $("#dashboard-capture-devices-header").show();
            deviceTable.empty();
            $.each(data, function(i, deviceName) {
                deviceTable.append('<tr><td class="dashboard-device-name">' +
                                       '<a href="#" title="Click to expand/collapse." data-toggle="collapse" data-target="#dashboard-device-collapse-' + i + '">' +
                                       '<div class="dashboard-device-lookup">' + deviceName + '</div></a>' +
                                       '<div class="dashboard-collapse collapse" id="dashboard-device-collapse-' + i + '">Updating...</div></td>' +
                                       "<td class=\"dashboard-status\"></td>" +
                                       "<td class=\"dashboard-locked\"></td>" +
                                       "<td class=\"dashboard-lineup\"></td>" +
                                       "<td class=\"dashboard-pool\"></td></tr>");
            });
        }

        $.get("rest/pool", "", function(data, status, xhr) {
            poolEnabled = data;

            if (data) {
                $(".dashboard-pool").show();
                $(".dashboard-pool-header").show();
                $(".dashboard-pool-footer").show();
            } else {
                $(".dashboard-pool").hide();
                $(".dashboard-pool-header").hide();
                $(".dashboard-pool-footer").hide();
            }
        });

        updateDashboard();
    }, "json");
}

function createManageUnloadedRows() {
    $.get("rest/unloadeddevices", "", function(data, status, xhr) {
        if (data.length == 0) {
            manageUnloadedTable.empty()
            $("#manage-unloaded-capture-devices-header").hide();
            $("#manage-add-unloaded-device").hide();
            manageUnloadedTable.append("<tr><td class=\"manage-unloaded-checked\">&nbsp;</td>" +
                                           "<td class=\"manage-unloaded-name\">There are no capture devices available to be loaded.</td>" +
                                           "<td class=\"manage-unloaded-description\">&nbsp;</td></tr>");
        } else {
            manageUnloadedTable.empty();
            $("#manage-unloaded-capture-devices-header").show();
            $("#manage-add-unloaded-device").show();
            $.each(data, function(i, unloadedDevice) {
                console.log( unloadedDevice );
                manageUnloadedTable.append("<tr><td class=\"manage-unloaded-checked\"><input class=\"manage-unloaded-checkbox\" type=\"checkbox\" value=\"" + unloadedDevice.ENCODER_NAME + "\"></td>" +
                                               "<td class=\"manage-unloaded-name\">" + unloadedDevice.ENCODER_NAME + "</td>" +
                                               "<td class=\"manage-unloaded-description\">" + unloadedDevice.DESCRIPTION + "</td></tr>");
            });
        }

        $(".manage-unloaded-checkbox").change(function() {
            manageUnloadedDevicesUpdateAddButton();
        });
    });
}

$(".manage-unloaded-checkbox-all").change(function() {
    $(".manage-unloaded-checkbox").prop('checked', this.checked);
    manageUnloadedDevicesUpdateAddButton();
});

function manageUnloadedDevicesUpdateAddButton() {
    var checkedBoxes = $(".manage-unloaded-checkbox:checked").length

    if (checkedBoxes > 1) {
        $("#manage-add-unloaded-device").html("Load Capture Devices");
        $("#manage-add-unloaded-device").removeClass("disabled");
    } else if (checkedBoxes == 0) {
        $("#manage-add-unloaded-device").html("Load Capture Device");
        $("#manage-add-unloaded-device").addClass("disabled");
    } else {
        $("#manage-add-unloaded-device").html("Load Capture Device");
        $("#manage-add-unloaded-device").removeClass("disabled");
    }
}

$("#manage-add-unloaded-device").on("click", function() {
    if ($(this).hasClass("disabled")) {
        return;
    }

    if (!confirm('Are you sure you want to load ' + $(".manage-unloaded-checkbox:checked").length + ' capture devices?')) {
        return;
    }

    $.each($(".manage-unloaded-checkbox:checked"), function(i, unloadedDeviceCheck) {
        var unloadedDeviceName = $(unloadedDeviceCheck).attr("value");

        $.get("rest/unloadeddevices/" + unloadedDeviceName + "/load", "", function(data, status, xhr) {
            if (status == "success") {
                $(unloadedDeviceCheck).parent().parent().remove();
            } else {
                alert("Error '" + status + "'. Unable to load the '" + unloadedDeviceName + "' capture device. See logs for details.");
            }
        });
    });
});