$(document).ready(function() {
    // Takes us to the dashboard by default page or returns us to the last page.
    if (window.location.hash != '') {
        $(window.location.hash + "-nav").addClass("active");
        $(window.location.hash + "-page").show();
        createDashboardRows();
    } else {
        $("#dashboard-nav").addClass("active");
        $("#dashboard-page").show();
        createDashboardRows();
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
});

/*
Dashboard content
=====================================
*/

var dashContent = $("#dashboard-content");
var deviceTable = $("#dashboard-capture-devices");

function createDashboardRows() {
    deviceTable.append("<tr><th class=\"dashboard-device-name-header\">Capture Device</th>" +
                           "<th class=\"dashboard-status-header\">SageTV Status</th>" +
                           "<th class=\"dashboard-locked-header\">Device Lock</th>" +
                           "<th class=\"dashboard-lineup-header\">Lineup</th>" +
                           "<th class=\"dashboard-pool-header\">Pool</th></tr>");

    $.get("rest/capturedevice", "", function(data, status, xhr) {
        $.each(data, function(i, deviceName) {
            deviceTable.append("<tr><td class=\"dashboard-device-name\">" + deviceName + "</td>" +
                                   "<td class=\"dashboard-status\"></td>" +
                                   "<td class=\"dashboard-locked\"></td>" +
                                   "<td class=\"dashboard-lineup\"></td>" +
                                   "<td class=\"dashboard-pool\"></td></tr>");
        });

        updateDashboard();
    }, "json");
}


function updateDashboard() {
    $.each(deviceTable.find("td.dashboard-device-name"), function(i, deviceName) {
        $.get("rest/capturedevice/" + $(deviceName).text() + "/details", "", function(data, status, xhr) {
            console.log ( data );
            $(deviceName).parent().find(".dashboard-status").append(data.locked ? "Active" : "Idle");
            $(deviceName).parent().find(".dashboard-lineup").append(data.channelLineup);
            $(deviceName).parent().find(".dashboard-pool").append(data.encoderPoolName);
        });

        $.get("rest/capturedevice/" + $(deviceName).text() + "/method/isExternalLocked", "", function(data, status, xhr) {
            if ($(deviceName).parent().find(".dashboard-status").text() == "Active") {
                $(deviceName).parent().find(".dashboard-locked").append(data.locked ? "External" : "SageTV");
            } else {
                $(deviceName).parent().find(".dashboard-locked").append(data.locked ? "Locked" : "Available");
            }
        });
    });
}

function getDetails(deviceName) {
    $.get("rest/capturedevice/" + deviceName + "/details", "", function(data, status, xhr) {
        /*$.each(data, function(i, deviceName) {
                $("#dashboard-content").append(deviceName);
        });*/
        console.log ( data.encoderName );
        createDashboardRow(data.encoderName);
    }, "json");
}

function createDashboardRow(deviceName) {
    $("#dashboard-capture-devices").append("<tr><td>" + deviceName + "</td></tr>")
}