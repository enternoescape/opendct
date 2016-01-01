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
        $("#manage-basic").show();
        $("#manage-advanced").hide();
        createManageParentRows();
        createManageLoadedRows();
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
        updateManageLoadedRows();
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
                                       '<a href="javascript:undefined" title="Click to expand/collapse details." data-toggle="collapse" data-target="#dashboard-device-collapse-' + i + '">' +
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
                $("#dashboard-pool-header").show();
                $("#dashboard-pool-footer").show();
            } else {
                $(".dashboard-pool").hide();
                $("#dashboard-pool-header").hide();
                $("#dashboard-pool-footer").hide();
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
                deviceDiv.append(' <span class="signal"></span> <span class="cci"></span>')

                $.get("rest/capturedevice/" + $(deviceName).text() + "/method/getSignalStrength", "", function(data, status, xhr) {
                    deviceDiv.find(".signal").html("Signal: " + data);
                });

                $.get("rest/capturedevice/" + $(deviceName).text() + "/method/getCopyProtection", "", function(data, status, xhr) {
                    deviceDiv.find(".cci").html("CCI: " + data);
                });
            } else {
                deviceDiv.html('<p/>There is no activity on this capture device.');
            }

            var statusDiv = $(deviceName).parent().parent().parent().find(".dashboard-status");
            statusDiv.html((data.locked ? "Active" : "Idle"));

            $.get("rest/lineup/" + data.channelLineup + "/details", "", function(data, status, xhr) {
                $(deviceName).parent().parent().parent().find(".dashboard-lineup").html(data.friendlyName);
            });

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
    $("#manage-apply-parent-device-changes").addClass("disabled");

    $.get("rest/capturedeviceparent", "", function(data, status, xhr) {

    if (data.length == 0) {
        $("#manage-loaded-parent-devices-header").addClass("hidden");
        $("#manage-apply-parent-device-changes").addClass("hidden");
        $("#manage-undo-parent-device-changes").addClass("hidden");
        manageLoadedParentTable.empty();
        manageLoadedParentTable.append(
            '<tr><td class=\"manage-parent-name\">There are no capture devices currently loaded.</td>' +
            "<td class=\"manage-is-network\"></td>" +
            "<td class=\"manage-remote-ip\"></td>" +
            "<td class=\"manage-local-ip\"></td>" +
            "<td class=\"manage-cablecard-present\"></td></tr>");
        } else {
            $("#manage-loaded-parent-devices-header").removeClass("hidden");
            $("#manage-apply-parent-device-changes").removeClass("hidden");
            $("#manage-undo-parent-device-changes").removeClass("hidden");
            manageLoadedParentTable.empty();
            $.each(data, function(i, parentName) {
                manageLoadedParentTable.append(
                    '<tr><td class="manage-parent-name">' +
                    '<a href="javascript:undefined" title="Click to show loaded child capture device names." data-toggle="collapse" data-target="#manage-parent-name-collapse-' + i + '">' +
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
                localAddressDiv.html('<a href="javascript:$(\'#manage-parent-device-local-ip-' + i + '\').hide()" title="Click to change the local IP address." data-toggle="collapse" data-target="#manage-parent-device-collapse-' + i + '">' +
                                     '<div class="manage-parent-device-local-ip-lookup" id="manage-parent-device-local-ip-' + i + '">' + data.localAddress + '</div></a>' +
                                     '<div class="manage-parent-device-collapse collapse" id="manage-parent-device-collapse-' + i + '">' +
                                     '<input type="text" class="form-control manage-parent-device-local-ip-value" value="' + data.localAddress + '"></div>');

                $(".manage-parent-device-local-ip-value").on("change keyup", function() {
                    managerLoadedParentCanApply();
                })
            } else {
                remoteAddressDiv.html("N/A");
                localAddressDiv.html("N/A");
            }

            var isCablecardDiv = $(parentName).parent().parent().parent().find(".manage-cablecard-present");
            isCablecardDiv.html((data.cableCardPresent ? "Yes" : "No"));
        });
    });
}

$("#manage-undo-parent-device-changes").on("click", function() {
    createManageParentRows();
});

function managerLoadedParentCanApply() {
    $("#manage-apply-parent-device-changes").removeClass("disabled");

    $.each($(".manage-parent-device-local-ip-value"), function(i, property) {
        var isNetworkDiv = $(property).parent().parent().parent().find(".manage-is-network");

        if (property.value == "" && isNetworkDiv.text() == "Yes") {
            $(property).parent().parent().addClass("background-error");
            $("#manage-apply-parent-device-changes").addClass("disabled");
        } else {
            $(property).parent().parent().removeClass("background-error");
        }
    });
}

$("#manage-apply-parent-device-changes").on("click", function() {
    if ($(this).hasClass("disabled")) {
        return;
    }

    if (!confirm('Are you sure you want to apply these changes?')) {
        return;
    }

    $("#manage-apply-parent-device-changes").addClass("disabled");

    $.each(manageLoadedParentTable.find("tr"), function(i, row) {
        var parentName = $(row).find(".manage-parent-lookup").text();
        var submit = new Object();

        submit.localAddress = $(row).find(".manage-parent-device-local-ip-value").val();

        var jsonResponse = JSON.stringify(submit);
        console.log(jsonResponse);

        $.ajax({
            type: "POST",
            url: "rest/capturedeviceparent/" + parentName + "/set",
            data: jsonResponse,
            contentType: "application/json",
            success: function(data, status, xhr) {
                createManageParentRows();
            },
            error : function(xhr, status, error) {
                alert("Error " + status + ". There was a problem while updating " + parentName + ". Check log for details.");
            }
        });
    });
});

function createManageLoadedRows() {
    $("#manage-remove-loaded-device").addClass("disabled");
    $("#manage-apply-loaded-device-changes").addClass("disabled");

    $(".manage-loaded-checkbox-all").prop('checked', false);

    $.get("rest/capturedevice", "", function(data, status, xhr) {
        if (data.length == 0) {
            $("#manage-loaded-devices-header").addClass("hidden");
            $("#manage-apply-loaded-device-changes").addClass("hidden");
            $("#manage-undo-loaded-device-changes").addClass("hidden");
            $("#manage-remove-loaded-device").addClass("hidden");
            manageLoadedTable.empty();
            manageLoadedTable.append('<tr><td class=\"manage-loaded-checked\">There are no capture devices currently loaded.</td>' +
                                   "<td class=\"manage-name\"></td>" +
                                   "<td class=\"manage-merit\"></td>" +
                                   "<td class=\"manage-force-unlock\"></td>" +
                                   "<td class=\"manage-consumer\"></td>" +
                                   "<td class=\"manage-lineup-empty\"></td>" +
                                   "<td class=\"manage-encoder-pool\"></td>" +
                                   "<td class=\"manage-advanced\"></td></tr>");
        } else {
            $("#manage-loaded-devices-header").removeClass("hidden");
            $("#manage-apply-loaded-device-changes").removeClass("hidden");
            $("#manage-undo-loaded-device-changes").removeClass("hidden");
            $("#manage-remove-loaded-device").removeClass("hidden");
            manageLoadedTable.empty();
            $.each(data, function(i, deviceName) {
                manageLoadedTable.append('<tr><td class="manage-loaded-checked"><input class="manage-loaded-checkbox" type="checkbox" value="' + deviceName + '"></td>' +
                                       '<td class="manage-device-name">' +
                                       '<a href="javascript:$(\'#manage-loaded-device-lookup-' + i + '\').hide()" title="Click to rename this capture device." data-toggle="collapse" data-target="#manage-loaded-device-collapse-' + i + '">' +
                                       '<div class="manage-loaded-device-lookup" id="manage-loaded-device-lookup-' + i + '">' + deviceName + '</div></a>' +
                                       '<div class="manage-loaded-collapse collapse" id="manage-loaded-device-collapse-' + i + '">' +
                                       '<input type="text" class="form-control manage-rename-device" value="' + deviceName + '"></div></td>' +

                                       "<td class=\"manage-merit\"></td>" +
                                       "<td class=\"manage-force-unlock\"></td>" +
                                       "<td class=\"manage-consumer\"></td>" +
                                       "<td class=\"manage-lineup\"></td>" +
                                       "<td class=\"manage-encoder-pool\"></td>" +
                                       '<td class="manage-advanced"><button title="Edit all available properties for this capture device." class="manage-advanced-button btn btn-primary" type="button">...</button></td></tr>');


            });

            $(".manage-rename-device").on("keyup change", function() {
                manageLoadedCanApply();

                if ($(this).parent().parent().parent().find(".manage-loaded-checkbox:checked").length == 0) {
                    return;
                }

                var applier = this;
                var newValue = $(this).val();

                $.each($(".manage-loaded-checkbox:checked"), function(i, checkBox) {

                    $(checkBox).parent().parent().find(".manage-loaded-device-lookup").hide();
                    $(checkBox).parent().parent().find(".manage-loaded-collapse").collapse('show');
                    var updateValue = $(checkBox).parent().parent().find(".manage-rename-device");

                    if (!($(updateValue)[0] === $(applier)[0])) {
                        $(updateValue).val(newValue + "-" + (i + 1));
                    }
                });
            });
        }

        $.get("rest/pool", "", function(data, status, xhr) {
            poolEnabled = data;

            if (data) {
                $(".manage-encoder-pool").show();
                $("#manage-encoder-pool-header").show();
                $("#manage-encoder-pool-footer").show();
            } else {
                $(".manage-encoder-pool").hide();
                $("#manage-encoder-pool-header").hide();
                $("#manage-encoder-pool-footer").hide();
            }
        });

        $(".manage-loaded-checkbox").change(function() {
            manageLoadedDevicesUnloadDevicesButton();
        });

        $.get("rest/lineup", "", function(data, status, xhr) {
            var lineupDropDown = $('<select class="form-control manage-lineup-value">');

            $.each(data, function(i, lineup) {
                $.get("rest/lineup/" + lineup + "/details", "", function(data, status, xhr) {
                    lineupDropDown.append('<option class="manage-lineup-values" value="' + lineup + '">' + data.friendlyName + '</option>');

                    $(".manage-lineup").html(lineupDropDown);
                });
            });
        });

        updateManageLoadedRows();
    }, "json");
}

function updateManageLoadedRows() {
    $("#manage-loaded-device-warning").empty();

    $.each(manageLoadedTable.find("div.manage-loaded-device-lookup"), function(i, deviceName) {
        $.get("rest/capturedevice/" + $(deviceName).text() + "/details", "", function(data, status, xhr) {
            if (data.locked == true) {
                $("#manage-loaded-device-warning").html("Warning: One or more capture devices are currently in use. Cells highlighted in dark red cannot be changed until the capture device is no longer in use. Any changes made will be discarded on apply. Capture devices in use cannot be unloaded. Some settings may not take effect until the capture device starts a new recording.");
                $(deviceName).parent().parent().addClass("background-locked");
                $(deviceName).parent().parent().parent().find(".manage-encoder-pool").addClass("background-locked");
            } else {
                $(deviceName).parent().parent().removeClass("background-locked");
                $(deviceName).parent().parent().parent().find(".manage-encoder-pool").removeClass("background-locked");
            }

            var meritDiv = $(deviceName).parent().parent().parent().find(".manage-merit");
            meritDiv.html('<input type="number" class="form-control manage-merit-value" min="0" max="2147483647" value="' + data.merit + '" />');

            $(".manage-merit-value").on("keyup change", function() {
                manageLoadedCanApply();

                if ($(this).parent().parent().find(".manage-loaded-checkbox:checked").length == 0) {
                    return;
                }

                var applier = this;
                var newValue = $(this).val();

                $.each($(".manage-loaded-checkbox:checked"), function(i, checkBox) {
                    var updateValue = $(checkBox).parent().parent().find(".manage-merit-value");

                    if (!($(updateValue)[0] === $(applier)[0])) {
                        $(updateValue).val(newValue);
                    }
                });
            });

            var forceDiv = $(deviceName).parent().parent().parent().find(".manage-force-unlock");
            forceDiv.html('<div class="centerCell"><input type="checkbox" class="manage-force-unlock-value"/></div>');
            forceDiv.find(".manage-force-unlock-value").prop("checked", data.alwaysForceExternalUnlock)

            $(".manage-force-unlock-value").on("change", function() {
                manageLoadedCanApply();

                if ($(this).parent().parent().parent().find(".manage-loaded-checkbox:checked").length == 0) {
                    return;
                }

                var applier = this;
                var newValue = $(this).prop("checked");

                $.each($(".manage-loaded-checkbox:checked"), function(i, checkBox) {
                    var updateValue = $(checkBox).parent().parent().find(".manage-force-unlock-value");

                    if (!($(updateValue)[0] === $(applier)[0])) {
                        $(updateValue).prop("checked", newValue);
                    }
                });
            });

            var consumerDiv = $(deviceName).parent().parent().parent().find(".manage-consumer");
            consumerDiv.html('<select class="form-control manage-consumer-value" name="manage-consumer-value">' +
                                '<option value="opendct.consumer.FFmpegSageTVConsumerImpl">FFmpeg</option>' +
                                '<option value="opendct.consumer.RawSageTVConsumerImpl">Raw</option>' +
                             '</select>');
            $(consumerDiv).find(".manage-consumer-value").val(data.consumer);

             $(".manage-consumer-value").on("change", function() {
                 manageLoadedCanApply();

                 if ($(this).parent().parent().find(".manage-loaded-checkbox:checked").length == 0) {
                     return;
                 }

                 var applier = this;
                 var newValue = $(this).val();

                 $.each($(".manage-loaded-checkbox:checked"), function(i, checkBox) {
                     var updateValue = $(checkBox).parent().parent().find(".manage-consumer-value");

                     if (!($(updateValue)[0] === $(applier)[0])) {
                         $(updateValue).val(newValue);
                     }
                 });
             });

            var lineupDiv = $(deviceName).parent().parent().parent().find(".manage-lineup");
            var lineupExists = false;

            $(lineupDiv.find('.manage-lineup-values')).each(function(){
                if (this.value == data.channelLineup) {
                    lineupExists = true;
                    this.selected = true;

                    // We don't want to add this trigger until the value is already set or we might
                    // end up with the apply button always enabled even when there are no changes.
                    $(lineupDiv.find('.manage-lineup-value')).on("change", function() {
                        manageLoadedCanApply();

                        if ($(this).parent().parent().find(".manage-loaded-checkbox:checked").length == 0) {
                            return;
                        }

                        var applier = this;
                        var newValue = $(this).val();

                        $.each($(".manage-loaded-checkbox:checked"), function(i, checkBox) {
                            var updateValue = $(checkBox).parent().parent().find(".manage-lineup-value");

                            if (!($(updateValue)[0] === $(applier)[0])) {
                             $(updateValue).val(newValue);
                            }
                        });
                    });
                }
            });

            if (lineupExists == false) {
                // The value will be populated asynchronously and we can't guarantee that it will
                // already be populated before this script, so we wait.

                var checkForLineup = setInterval(function() {
                    var lineupExists = false;

                    $(lineupDiv.find('.manage-lineup-values')).each(function(){
                        if (this.value == (data.channelLineup)) {
                            lineupExists = true;
                            this.selected = true;

                            // We don't want to add this trigger until the value is already set or we might
                            // end up with the apply button always enabled even when there are no changes.
                            $(lineupDiv.find('.manage-lineup-value')).on("change", function() {
                                manageLoadedCanApply();

                                if ($(this).parent().parent().find(".manage-loaded-checkbox:checked").length == 0) {
                                    return;
                                }

                                var applier = this;
                                var newValue = $(this).val();

                                $.each($(".manage-loaded-checkbox:checked"), function(i, checkBox) {
                                    var updateValue = $(checkBox).parent().parent().find(".manage-lineup-value");

                                    if (!($(updateValue)[0] === $(applier)[0])) {
                                        $(updateValue).val(newValue);
                                    }
                                });
                            });
                        }
                    });

                    if (lineupExists == true) {
                        clearInterval(checkForLineup);
                    }
                }, 100);
            }

            var poolDiv = $(deviceName).parent().parent().parent().find(".manage-encoder-pool");
            poolDiv.html('<input type="text" class="form-control manage-encoder-pool-value" value="' + (data.encoderPoolName) + '">');

            $(".manage-encoder-pool-value").on("keyup change", function() {
                manageLoadedCanApply();

                if ($(this).parent().parent().find(".manage-loaded-checkbox:checked").length == 0) {
                    return;
                }

                var applier = this;
                var newValue = $(this).val();

                $.each($(".manage-loaded-checkbox:checked"), function(i, checkBox) {
                    var updateValue = $(checkBox).parent().parent().find(".manage-encoder-pool-value");

                    if (!($(updateValue)[0] === $(applier)[0])) {
                        $(updateValue).val(newValue);
                    }
                });
            });
        });
    });
}

$("#manage-undo-loaded-device-changes").on("click", function() {
    createManageLoadedRows();
});

$(".manage-loaded-checkbox-all").change(function() {
    $(".manage-loaded-checkbox").prop('checked', this.checked);
    manageLoadedDevicesUnloadDevicesButton();
});

function manageLoadedCanApply() {

    $("#manage-apply-loaded-device-changes").removeClass("disabled");

    $.each($(".manage-rename-device"), function(i, property) {
        if (property.value == "") {
            $(property).parent().parent().addClass("background-error");
            $("#manage-apply-loaded-device-changes").addClass("disabled");
        } else {
            $(property).parent().parent().removeClass("background-error");
        }
    });

    $.each($(".manage-merit-value"), function(i, property) {
        if (property.value < 0) {
            $(property).parent().addClass("background-error");
            $("#manage-apply-loaded-device-changes").addClass("disabled");
        } else {
            $(property).parent().removeClass("background-error");
        }
    });
}

$("#manage-apply-loaded-device-changes").on("click", function() {
    if ($(this).hasClass("disabled")) {
        return;
    }

    if (!confirm('Are you sure you want to apply these changes?')) {
        return;
    }

    $.each(manageLoadedTable.find("tr"), function(i, row) {
        var deviceName = $(row).find(".manage-loaded-device-lookup").text();
        var submit = new Object();

        submit.encoderName = $(row).find(".manage-rename-device").val();
        submit.merit = $(row).find(".manage-merit-value").val();
        submit.alwaysForceExternalUnlock = $(row).find(".manage-force-unlock-value").prop("checked");
        submit.consumer = $(row).find(".manage-consumer-value").val();
        submit.channelLineup = $(row).find(".manage-lineup-value").val();
        submit.encoderPoolName = $(row).find(".manage-encoder-pool-value").val();

        var jsonResponse = JSON.stringify(submit);
        console.log(jsonResponse);

        $.ajax({
            type: "POST",
            url: "rest/capturedevice/" + deviceName + "/set",
            data: jsonResponse,
            contentType: "application/json",
            success: function(data, status, xhr) {
                createManageLoadedRows();
            },
            error : function(xhr, status, error) {
                alert("Error " + status + ". There was a problem while updating " + deviceName + ". Check log for details.");
            }
        });
    });
});

function manageLoadedDevicesUnloadDevicesButton() {
    var checkedBoxes = $(".manage-loaded-checkbox:checked").length;

    if (checkedBoxes > 1) {
        $("#manage-remove-loaded-device").html("Unload Selected Capture Devices");
        $("#manage-remove-loaded-device").removeClass("disabled");
    } else if (checkedBoxes == 0) {
        $("#manage-remove-loaded-device").html("Unload Selected Capture Device");
        $("#manage-remove-loaded-device").addClass("disabled");
    } else {
        $("#manage-remove-loaded-device").html("Unload Selected Capture Device");
        $("#manage-remove-loaded-device").removeClass("disabled");
    }
}

$("#manage-remove-loaded-device").on("click", function() {
    if ($(this).hasClass("disabled")) {
        return;
    }

    if (!confirm('Are you sure you want to unload ' + $(".manage-loaded-checkbox:checked").length + ' capture devices?')) {
        return;
    }

    $("#manage-remove-loaded-device").html("Unload Selected Capture Device");
    $("#manage-remove-loaded-device").addClass("disabled");

    $.each($(".manage-loaded-checkbox:checked"), function(i, loadedDeviceCheck) {
        var loadedDeviceName = $(loadedDeviceCheck).attr("value");

        if ($(loadedDeviceCheck).closest("tr").find(".manage-loaded-device-lookup").closest("td").hasClass("background-locked")) {
            createManageLoadedRows();
            return true;
        }

        $.get("rest/unloadeddevices/" + loadedDeviceName + "/unload", "", function(data, status, xhr) {
            if (status == "success") {
                $(loadedDeviceCheck).parent().parent().remove();
            } else {
                alert("Error '" + status + "'. Unable to load the '" + loadedDeviceName + "' capture device. See logs for details.");
            }

            createManageParentRows();
            createManageUnloadedRows();

            if ($(".manage-loaded-checkbox:checked").length == 0) {
                createManageLoadedRows();
            }
        });
    });
});

function createManageUnloadedRows() {
    $(".manage-unloaded-checkbox-all").prop('checked', false);

    $.get("rest/unloadeddevices", "", function(data, status, xhr) {
        if (data.length == 0) {
            manageUnloadedTable.empty();
            $("#manage-unloaded-capture-devices-header").addClass("hidden");
            $("#manage-add-unloaded-device").addClass("hidden");
            manageUnloadedTable.append("<tr><td class=\"manage-unloaded-checked\">There are no capture devices available to be loaded.</td>" +
                                           "<td class=\"manage-unloaded-name\"></td>" +
                                           "<td class=\"manage-unloaded-description\">&nbsp;</td></tr>");
        } else {
            manageUnloadedTable.empty();
            $("#manage-unloaded-capture-devices-header").removeClass("hidden");
            $("#manage-add-unloaded-device").removeClass("hidden");
            $.each(data, function(i, unloadedDevice) {
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
    var checkedBoxes = $(".manage-unloaded-checkbox:checked").length;

    if (checkedBoxes > 1) {
        $("#manage-add-unloaded-device").html("Load Selected Capture Devices");
        $("#manage-add-unloaded-device").removeClass("disabled");
    } else if (checkedBoxes == 0) {
        $("#manage-add-unloaded-device").html("Load Selected Capture Device");
        $("#manage-add-unloaded-device").addClass("disabled");
    } else {
        $("#manage-add-unloaded-device").html("Load Selected Capture Device");
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

    $("#manage-add-unloaded-device").html("Load Selected Capture Device");
    $("#manage-add-unloaded-device").addClass("disabled");

    $.each($(".manage-unloaded-checkbox:checked"), function(i, unloadedDeviceCheck) {
        var unloadedDeviceName = $(unloadedDeviceCheck).attr("value");

        $.get("rest/unloadeddevices/" + unloadedDeviceName + "/load", "", function(data, status, xhr) {
            if (status == "success") {
                $(unloadedDeviceCheck).parent().parent().remove();
            } else {
                alert("Error '" + status + "'. Unable to load the '" + unloadedDeviceName + "' capture device. See logs for details.");
            }

            createManageParentRows();
            createManageLoadedRows();

            if ($(".manage-unloaded-checkbox:checked").length == 0) {
                createManageUnloadedRows();
            }
        });
    });
});