app.controller('ProjectController', function($rootScope, $scope, $http, $stateParams, $interval, $location) {

  $scope.projectId = $stateParams.id;
  $scope.status = $stateParams.status;

  $scope.getLatest = function() {
    app.cancelRefresh($scope, $interval);

    $scope.url = app.apiHost($location) + '/api/project/' + $scope.projectId + '/latest';

    if ($scope.status != null) {
      $scope.url = $scope.url + '/' + $scope.status;
    }

    $scope.promise = $http.get($scope.url);
    $scope.promise
      .success(function(data){
        $scope.job = data;
        $location.path('/job/' + $scope.job.id)
      })
      .error(function(data, status, headers, config) {
      });
  }

  $scope.getLatest();
});

