app.controller('NavController', function($scope, $rootScope, $cookies, $timeout) {
  $scope.$on('clearNotifications', function(event, args) {
    $scope.dismissAlert();
    $scope.dismissError();
    $scope.dismissWarning();
  });
  
  $scope.$on('connected', function(event, args) {
    $scope.connected = true
  });

  $scope.$on('disconnected', function(event, args) {
    $scope.connected = false
  });

  $scope.$on('setAlert', function(event, args) {
    $scope.dismissAlert();
    $scope.dismissError();
    $scope.dismissWarning();
    $scope.alertMessage = args.msg;
  });

  $scope.dismissAlert = function() {
    $scope.alertMessage = undefined;
  }

  $scope.$on('setError', function(event, args) {
    $scope.dismissAlert();
    $scope.dismissError();
    $scope.dismissWarning();
    $scope.errorMessage = args.msg;
  });

  $scope.dismissError = function() {
    $scope.errorMessage = undefined;
  }

  $scope.$on('setWarning', function(event, args) {
    $scope.dismissAlert();
    $scope.dismissError();
    $scope.dismissWarning();
    $scope.warningMessage = args.msg;
  });

  $scope.dismissWarning = function() {
    $scope.warningMessage = undefined;
  }
});
