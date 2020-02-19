app.controller('DashboardController', function($rootScope, $scope) {
  $rootScope.page = 'Dashboard';
  $scope.chartLimits = ["10", "20", "30", "40"];

  $scope.topDurationLimit = app.topDurationLimit;
  $scope.onChangeTopDurationLimit = function(value) {
    app.topDurationLimit = value;
    app.updateCharts();
  };

  $scope.topCommittersLimit = app.topCommittersLimit;
  $scope.onChangeTopCommittersLimit = function(value) {
    app.topCommittersLimit = value;
    app.updateCharts();
  };

  $scope.wallOfShameLimit = app.wallOfShameLimit;
  $scope.onChangeWallOfShameLimit = function(value) {
    app.wallOfShameLimit = value;
    app.updateCharts();
  };

  $scope.topProjectsLimit = app.topProjectsLimit;
  $scope.onChangeTopProjectsLimit = function(value) {
    app.topProjectsLimit = value;
    app.updateCharts();
  };

  $scope.$on('$locationChangeStart', function(event, next, current) {
    app.updateCharts(null, true);
  });
});
