app.controller('ProjectListController', function($rootScope, $scope, $http, $interval, $location) {
  $rootScope.page = 'Projects';
  $scope.Math = window.Math;
  $scope.sortField = "project";
  $scope.sortReverse = false;

  $scope.pauseProject = function(project) {
    $http.get(app.apiHost($location) + '/api/project/' + project + '/pause')
      .success(function(data){})
  }

  $scope.unpauseProject = function(project) {
    $http.get(app.apiHost($location) + '/api/project/' + project + '/unpause')
      .success(function(data){})
  }
});
