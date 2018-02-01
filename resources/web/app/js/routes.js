app.config(function($stateProvider, $urlRouterProvider) {
    $urlRouterProvider.otherwise('/dashboard')

    $stateProvider 
        .state({ name:'dashboard',   url:'/dashboard',                  templateUrl:'/views/partials/dashboard.partial' })
        .state({ name:'wallboard',   url:'/wallboard',                  templateUrl:'/views/partials/wallboard.partial' })
        .state({ name:'projects',    url:'/projects',                   templateUrl:'/views/partials/projects/projectlist.partial' })
        .state({ name:'latest',      url:'/project/:id/latest',         templateUrl:'/views/partials/projects/project.partial' })
        .state({ name:'status',      url:'/project/:id/latest/:status', templateUrl:'/views/partials/projects/project.partial' })
        .state({ name:'jobs',        url:'/jobs/:id',                   templateUrl:'/views/partials/jobs/joblist.partial' })
        .state({ name:'job',         url:'/job/:id',                    templateUrl:'/views/partials/jobs/jobdetail.partial' })
        .state({ name:'about',       url:'/about',                      templateUrl:'/views/partials/about/about.partial' });
});