$(document).ready(function() {
    $(".navbar").find(".nav").find(".active").removeClass('active');

    if (window.location.hash != '') {
        $(window.location.hash + "-page").show();
        $(window.location.hash + "-nav").addClass("active");
    } else {
        $("#dashboard-page").show();
        $("#dashboard-nav").addClass("active");
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