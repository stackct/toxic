app.controller('AboutController', function($rootScope, $scope, $http, $interval, $location) {
  $rootScope.page = 'About';

  $scope.loadAbout = function() {
    $scope.aboutPromise = $http.get(app.apiHost($location) + '/api/about');
    $scope.aboutPromise
      .success(function(data) {
        $scope.about = data;
      })
      .error(function(data, status, headers, config) {
      })
  }
  $scope.loadAbout();
});
