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
            deviceTable.append('<tr><td><button class="btn btn-primary" onclick="changePage(\'#manage\');">Add Capture Devices</button></td>' +
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
            console.log ( data );

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

var manageUnloadedTable = $("#manager-unloaded-capture-devices-body");

function createManageUnloadedRows() {
    $.get("rest/unloadeddevices", "", function(data, status, xhr) {
        if (data.length > 0) {
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
            $.each(data, function(i, deviceName) {
                manageUnloadedTable.append("<tr><td class=\"manage-unloaded-checked\">&nbsp;</td>" +
                                               "<td class=\"manage-unloaded-name\">&nbsp;</td>" +
                                               "<td class=\"manage-unloaded-description\">&nbsp;</td></tr>");
            });
        }
    });
}