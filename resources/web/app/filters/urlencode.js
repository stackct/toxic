app.filter('urlEncode', function() {
    return function(input) {
        return window.encodeURIComponent(input);
    }
});
