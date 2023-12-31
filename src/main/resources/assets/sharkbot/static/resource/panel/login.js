export function getCookie(name) {
    let item, result, cookies =document.cookie.split(";");

    for (let i = 0; i < cookies.length; i++) {
        item = cookies[i].substring(0, cookies[i].indexOf("="));
        result = cookies[i].substring(cookies[i].indexOf("=")+1);
        item = item.replace(/^\s+|\s+$/g,"");
        if (item === name) {
            return decodeURI(result);
        }
    }
}

export function getToken() {
    let token = getCookie("sharkBotAccessToken");
    if (token) return token;
}

export function getTokenElseLogin() {
    let token = getToken();
    if (token) return token;
    else {
        location.href = "/login?redirect=" + encodeURI(location.href);
    }
}
