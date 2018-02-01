app.controller('StatusController', function($rootScope, $scope, $http, $interval, $location) {
  $scope.echo = function() {
    $interval.cancel(app.statusRefresh);

    $scope.promise = $http.get(app.apiHost($location) + '/api/echo');
    $scope.promise
      .success(function(data) {
        $rootScope.$broadcast('connected', data);
        $rootScope.$broadcast('authenticated', data);
      })
      .error(function(data, status, headers, config) {
        $rootScope.$broadcast('disconnected', data);
      })
      .finally(function(data) {
        app.statusRefresh = app.refreshIsolated($scope, $interval, $scope.echo, app.refreshDelaySeconds * 2000);
      });
  }

  $scope.shouldRefresh = function() {
    return true;  // Always refresh status
  }

  $scope.echo();
});
