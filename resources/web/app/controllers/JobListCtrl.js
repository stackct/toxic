app.controller('JobListController', function($rootScope, $scope, $http, $interval, $stateParams, $location) {
  $rootScope.page = 'Jobs';
  $scope.Math = window.Math;

  $scope.project = {
    id: $stateParams.id,
    jobs: []
  };
  $scope.sortField = ['project','sequence'];
  $scope.sortReverse = true;

  if ($rootScope.environment.jobMetrics != null) {
    $scope.projectMetrics = $rootScope.environment.jobMetrics.projectMetrics[$scope.project.id];
  }

  $scope.busy = function() { 
    return !app.refreshing ? $scope.promise : null 
  }

  $scope.shouldRefresh = function() {
    // JobList can always be refreshed
    return (['About','Environment'].indexOf($rootScope.page) == -1)
  }
  
  $scope.renderJobs = function() {
    app.cancelRefresh($scope, $interval);
    $scope.promise = $http.get(app.apiHost($location) + '/api/jobs/' + $stateParams.id);
    $scope.promise
      .success(function(data){
        $scope.project.jobs = data;
        app.refresh($scope, $interval, $scope.renderJobs)
      })
      .error(function(data, status, headers, config) {
        app.refresh($scope, $interval, $scope.renderJobs)
      });
  }

  $scope.project.jobs = [];
  $rootScope.$broadcast('clearNotifications', {});
  $scope.renderJobs();
});

