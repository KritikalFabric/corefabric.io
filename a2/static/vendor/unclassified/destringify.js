var __window = window;
var destringify = function(date) {
    if (date != null && 'string' === typeof(date)) return new Date(date);
    return date;
}
var stringify = function(date) {
    if (!date) return null;
    return dateFormat(date, "UTC:yyyy-mm-dd'T'HH:MM:ss'Z'");
}
var stringreplaceall = function(target, search, replacement) {
    return target.split(search).join(replacement);
};
var grabiframecontentdocument = function(id) {
    return document.getElementById(id).contentWindow.document;
};
var grabcontentdocument = function(selector, parent) {
    return jQuery(selector, parent).get(0).contentWindow.document;
};