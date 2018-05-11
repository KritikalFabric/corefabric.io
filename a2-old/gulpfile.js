/// <binding BeforeBuild='build' Clean='clean' ProjectOpened='default' />
var ts = require('gulp-typescript');
var gulp = require('gulp');
var clean = require('gulp-clean');

var destPath = '../build/resources/main/web/a2/';

// Delete the dist directory
gulp.task('clean', function () {
    return gulp.src(destPath)
        .pipe(clean());
});

gulp.task("scriptsNStyles", function() {
    gulp.src([
        'core-js/client/**',
        'systemjs/dist/system.src.js',
        'reflect-metadata/**',
        'rxjs/**',
        'zone.js/dist/**',
        '@angular/**',
        'jquery/dist/jquery.*js',
        'bootstrap/dist/js/bootstrap.*js'
    ], {
        cwd: "node_modules/**"
    })
        .pipe(gulp.dest("../build/resources/main/web/a2/node_modules"));
    gulp.src([
        'app/**',
        'download/**',
        'static/**',
        'index.html',
        'styles.css',
        'systemjs.config.js'
    ], {
        cwd: './**'
    })
        .pipe(gulp.dest("../build/resources/main/web/a2"));
});

var tsProject = ts.createProject('./tsconfig.json');
gulp.task('ts', function (done) {
    //var tsResult = tsProject.src()
    var tsResult = gulp.src([
        "app/*.ts"
    ])
        .pipe(ts(tsProject));
    return tsResult;
});

gulp.task('default', ['scriptsNStyles', 'ts']);