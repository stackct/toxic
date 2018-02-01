app.controller('AuthController', function($rootScope, $cookies, $scope, $http, $interval, $location) {
  $scope.authType = "Slack";

  $scope.requestAuth = function(auth, authType) {
    if (!auth || auth.length < 1) {
      return;
    } 
    $rootScope.$broadcast('setAlert', {msg:"Requesting authentication ..."});
    $scope.promise = $http.get(app.apiHost($location) + '/api/authrequest?auth=' + encodeURIComponent(auth) + "&type=" + authType + "&loc=" + encodeURIComponent($location.absUrl()));
    $scope.promise.success(function(data) {
      $rootScope.$broadcast('setAlert', {msg:"An authentication request has been sent to " + auth});
    });
    $scope.promise.error(function(data) {
      $rootScope.$broadcast('setError', {msg:"Unable to send authentication request to " + auth});
    });
  }

  $scope.validateToken = function() {
    if (!$scope.token || $scope.token.length < 1) {
      return;
    } 
    $rootScope.$broadcast('setAlert', {msg:"Validating authentication token ..."});
    $scope.promise = $http.get(app.apiHost($location) + '/api/authvalidate?token=' + encodeURIComponent($scope.token));
    $scope.token = null;
    $scope.promise.success(function(data) {
      $rootScope.$broadcast('setAlert', {msg:"Successfully validated authentication token; your user info will update shortly"});
      $location.url($location.path())
    });
    $scope.promise.error(function(data) {
      $rootScope.$broadcast('setError', {msg:"Unable to validate authentication token"});
    });
  }

  $scope.token = $location.search().token;
  if ($scope.token) {
    $scope.validateToken();
  }  

  $scope.$on('authenticated', function(event, args) {
    $scope.authenticated = args.authenticated;
    $scope.authId = args.auth;
    $scope.user = args;
    $rootScope.user = args;
  });
});
