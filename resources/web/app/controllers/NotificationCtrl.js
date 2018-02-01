app.controller('NotificationController', function($rootScope, $scope, $http, $interval, $location) {
  $scope.$on('connected', function(event) {
    $scope.connected = true;
  });

  $scope.$on('disconnected', function(event) {
    $scope.connected = false;
  });
});
