package HTTPServer;

import java.io.*;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class HTTPServerHandler {
    private String startLine;//请求报文的开始行
    private String header;//请求报文的首部
    private Map<String,String>headersMap=new HashMap<>();//存储请求报文的首部和对应的值
    private byte[]content;//请求体（如果有的话）

    private Map<String,String> FileMovement = new HashMap<>();//储存已经被移动的文件名已经移动的情况（临时移动 or 永久移动）
    private Map<String,String> FileNewAddr = new HashMap<>();//储存已经被移动的文件的新地址

    private static Map<String,String> Account = new HashMap<>();//储存注册的账户
    private static Map<String,String> CookieToAccount = new HashMap<>();//储存已经登录的用户的cookie和对应的用户
    private static Map<String,String> AccountToCookie = new HashMap<>();//储存已登录的用户和对应的cookie
    private static int LoggedUser = 0;//储存已经登录的用户数目

    HTTPServerHandler(byte[] b){

        //获得请求报文的首行
        StringBuilder startLinebuilder = new StringBuilder();
        int startLineEndIndex=0;
        for(int i=0;i<b.length;i++){
            startLinebuilder.append((char) b[i]);
            if(b[i]=='\r'){
                if(b[i+1]=='\n'){
                    startLineEndIndex=i;
                    break;
                }
            }
        }
        startLine=startLinebuilder.toString().substring(0,startLinebuilder.length()-1);

        //获得请求报文的头部
        StringBuilder headerBuilder = new StringBuilder();
        int headerEndIndex=0;
        for(int i=startLineEndIndex+2;i<b.length;i++){
            headerBuilder.append((char) b[i]);
            if(b[i]=='\r'){
                if (b[i+1]=='\n'
                        && b[i+2]=='\r'
                        && b[i+3]=='\n'){
                    headerEndIndex=i;
                    break;
                }
            }
        }
        header=headerBuilder.toString().substring(0,headerBuilder.length()-1);

        //将头部的名称和值加入到headersMap
        String[] headerSplit = header.split("\r\n");
        for(int i=0;i<headerSplit.length;i++){
            String[] headerTemp = headerSplit[i].split(":");
            String name = headerTemp[0];
            StringBuilder valueBuilder = new StringBuilder();
            for(int j=1;j<headerTemp.length;j++){
                if(j>1){
                    valueBuilder.append(":");
                }
                valueBuilder.append(headerTemp[j]);
            }
            String value = valueBuilder.toString().substring(1);
            headersMap.put(name,value);
        }

        //获得请求报文的内容
        content = new byte[b.length-headerEndIndex-4];
        for(int i=headerEndIndex+4;i<b.length;i++){
            content[i-headerEndIndex-4]=b[i];
        }
    }

    //获得响应报文，除了GET和POST外的方法不支持，返回状态码405
    byte[] Response(){
        String Method = startLine.split(" ")[0];
        if(Method.equals("GET")){
            return GETMethod();
        }else if(Method.equals("POST")){
            return POSTMethod();
        }else{
            return status_405();
        }
    }

    //GET方法处理
    byte[] GETMethod(){
        //获得GET方法要获取的文件的路径、名称、文件类型
        String[] startLineSplit = startLine.split(" ");
        String url = startLineSplit[1];
        url=url.substring(1);
        File f = new File(url);
        String[] addrSplit = url.split("/");
        String FileName = addrSplit[addrSplit.length - 1];
        String FileType = FileName.split("\\.")[FileName.split("\\.").length - 1];
        //ContentType要放在响应报文的首部
        String ContentType = "";
        //GET方法支持的类型：txt、html、jpg
        switch (FileType) {
            case "txt":
                ContentType += "text/plain";
                break;
            case "html":
            case "htm":
                ContentType += "text/html";
                break;
            case "jpg":
            case "jpeg":
                ContentType += "image/jpeg";
                break;
        }
        if(!f.exists()){//文件不存在url的情况：文件不存在（404）、文件永久转移（301）、文件临时转移（302）
            FileMovementInitialize();
            String movement = FileMovement.get(FileName);
            String newAddr = FileNewAddr.get(FileName);
            if(movement==null){
                return status_404();
            }else if(movement.equals("permanently")){
                return status_301(newAddr);
            }else {
                return status_302(newAddr);
            }

        }else {
            long lastModified = f.lastModified();
            if(headersMap.get("If-Modified-Since")!=null) {//如果headers有“If-Modified-Since”则需判断文件是否在这个时间以后修改了，如果没修改则返回304状态码
                String ModifiedSinceTime = headersMap.get("If-Modified-Since");
                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                try {
                    String lastModifiedFormatTime = getFormatTime(lastModified);
                    if (!((dateFormat.parse(lastModifiedFormatTime)).after(dateFormat.parse(ModifiedSinceTime)))) {
                        return status_304();
                    }
                }catch (ParseException e){
                    e.printStackTrace();
                    return status_500();
                }
            }
            byte[] body = null;
            try {
                InputStream inputStream = new FileInputStream(f);
                body = new byte[inputStream.available()];
                inputStream.read(body);
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                return status_500();
            }

            return GETSuccess(body, ContentType,lastModified);
        }
    }

    void FileMovementInitialize(){//文件移动情况初始化函数
        FileMovement.put("File3.html","permanently");
        FileNewAddr.put("File3.html","/src/NewAddress/File3.html");
        FileMovement.put("File3.jpg","permanently");
        FileNewAddr.put("File3.jpg","/src/NewAddress/File3.jpg");
        FileMovement.put("File3.txt","permanently");
        FileNewAddr.put("File3.txt","/src/NewAddress/File3.txt");

        FileMovement.put("File4.html","temporarily");
        FileNewAddr.put("File4.html","/src/NewAddress/File4.html");
        FileMovement.put("File4.jpg","temporarily");
        FileNewAddr.put("File4.jpg","/src/NewAddress/File4.jpg");
        FileMovement.put("File4.txt","temporarily");
        FileNewAddr.put("File4.txt","/src/NewAddress/File4.txt");
    }

    byte[] status_404(){//404状态码：请求的资源找不到
        String response = "";
        response+="HTTP/1.1 404 Not Found\r\n";
        response+="Date: "+getServerTime()+"\r\n";
        response+="Content-Type: "+"text/plain"+ "\r\n";
        String body = "We can't find this file.";
        response+="Content-Length: "+body.length()+"\r\n";
        response+="\r\n";
        response+=body;
        return response.getBytes();
    }

    byte[]status_301(String newAddr){//301状态码：资源永久移动，newAddr是新地址
        String response = "";
        response+="HTTP/1.1 301 Moved Permanently\r\n";
        response+="Date: "+getServerTime()+"\r\n";
        response+="Location: "+newAddr+ "\r\n";
        response+="Content-Type: "+"text/plain"+ "\r\n";
        String body = "New address is "+newAddr;
        response+="Content-Length: "+body.length()+"\r\n";
        response+="\r\n";
        response+=body;
        return response.getBytes();
    }

    byte[]status_302(String newAddr){//302状态码：资源临时移动，newAddr是临时地址
        String response = "";
        response+="HTTP/1.1 302 Found\r\n";
        response+="Date: "+getServerTime()+"\r\n";
        response+="Location: "+newAddr+ "\r\n";
        response+="Content-Type: "+"text/plain"+ "\r\n";
        String body = "Temporary address is "+newAddr;
        response+="Content-Length: "+body.length()+"\r\n";
        response+="\r\n";
        response+=body;
        return response.getBytes();
    }

    byte[]status_304(){//304状态码：请求的文件未修改
        String response = "";
        response+="HTTP/1.1 304 Not Modified\r\n";
        response+="Date: "+getServerTime()+"\r\n";
        response+="\r\n";
        return response.getBytes();
    }

    byte[] GETSuccess(byte[]body, String ContentType,long lastModified){//GET方法成功，也就是GET方法状态码为200的时候，body为资源内容，ContentType为文件类型，lastModified是文件最近修改日期
        String response = "";
        response+="HTTP/1.1 200 OK\r\n";
        response+="Date: "+getServerTime()+"\r\n";
        response+="Content-Type: "+ContentType+ "\r\n";
        response+="Content-Length: "+body.length+"\r\n";
        response+="Last-Modified: "+getFormatTime(lastModified)+"\r\n";
        response+="\r\n";
        byte[] re = new byte[response.length()+body.length];
        System.arraycopy(response.getBytes(),0,re,0,response.getBytes().length);
        System.arraycopy(body,0,re,response.getBytes().length,body.length);
        return re;
    }

    byte[] POSTMethod(){//POST方法处理
        String[] startLineSplit = startLine.split(" ");
        String url = startLineSplit[1];
        String ContentType = headersMap.get("Content-Type");
        String ContentLen = headersMap.get("Content-Length");
        //判断cookie.如果cookie有效且为登录请求则直接登录
        String cookie = headersMap.get("Cookie");
        if(cookie!=null){
            if(url.equals("/login")) {
                String account = CookieToAccount.get(cookie);
                if (account != null) {
                    return POSTSuccess(account + ",you are logged in.");
                }
            }
        }

        //POST方法支持的类型：application/x-www-form-urlencoded
        if(ContentType==null){
            return POSTSuccess("POST Success,but this content type is none.We just support application/x-www-form-urlencoded.");
        }
        if((ContentType).startsWith("application/x-www-form-urlencoded")){
            if(ContentLen==null||content.length!=Integer.parseInt(ContentLen)){
                return status_500();
            }else {//添加登录和注册接口
                if (url.equals("/register")) {
                    return register(content);
                } else if (url.equals("/login")) {
                    return login(content);
                } else {
                    return POSTSuccess("POST Success,but we don't support this.");
                }
            }
        }else{
            return POSTSuccess("POST Success,but we don't support this mime.We just support application/x-www-form-urlencoded.");
        }
    }

    byte[] status_500(){//500状态码：服务器出现错误
        String response = "";
        response+="HTTP/1.1 500 Internal Server Error\r\n";
        response+="Date: "+getServerTime()+"\r\n";
        response+="Content-Type: "+"text/plain"+ "\r\n";
        String body = "The server has some errors.";
        response+="Content-Length: "+body.length()+"\r\n";
        response+="\r\n";
        response+=body;
        return response.getBytes();
    }

    byte[] POSTSuccess(String Content){//POST方法成功时的响应报文，Content是具体内容
        String response = "";
        response+="HTTP/1.1 200 OK\r\n";
        response+="Date: "+getServerTime()+"\r\n";
        byte[] Contentbytes = Content.getBytes();
        response+="Content-Type: text/plain\r\n";
        response+="Content-Length: "+Contentbytes.length+"\r\n";
        response+="\r\n";
        response+=Content;
        return response.getBytes();
    }

    byte[] status_405(){//405状态码：请求的方法不支持
        String response = "";
        response+="HTTP/1.1 405 Method Not Allowed\r\n";
        response+="Date: "+getServerTime()+"\r\n";
        response+="Content-Type: "+"text/plain"+ "\r\n";
        String body = "This method is not allow,because we don't support it.";
        response+="Content-Length: "+body.length()+"\r\n";
        response+="\r\n";
        response+=body;
        return response.getBytes();
    }

    String getFormatTime(long time){//获得标准格式的时间表示
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(time);
    }

    String getServerTime() {//获得现在时间的标准格式表示
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(calendar.getTime());
    }

    boolean isClosed(){//默认开启长连接，请求报文的首部Connection为close则关闭长连接
        String status = headersMap.get("Connection");
        if (status!=null&&status.equals("close")){
            return true;
        }
        return false;
    }

    //账号密码格式 ：username=xxxxxx&password=xxxxxx
    byte[] register(byte[]body){//处理注册函数
        String[]tmp = parseUsernameAndPassword(body);
        if(tmp==null){
            return status_500();
        }
        if(Account.get(tmp[0])!=null){//账号已经被注册过了
            return POSTSuccess("Register fail,this account was registered.");
        }else {
            Account.put(tmp[0], tmp[1]);
            return POSTSuccess("Register succeed,you have a new account:\n" + "username: " + tmp[0] + "\npassword: " + tmp[1]);
        }
    }

    byte[] login(byte[]body){//处理登录的函数
        String[]tmp = parseUsernameAndPassword(body);
        if(tmp==null){
            return status_500();
        }
        String password = Account.get(tmp[0]);
        if(password==null){//账号不存在的情况
            return POSTSuccess("Login fail.This account don't exist.");
        }else if(!password.equals(tmp[1])){//密码不对的情况
            return POSTSuccess("Login fail.Your password is wrong.");
        }else {//登录成功，并发送cookie
            String Cookie = AccountToCookie.get(tmp[0]);
            if(Cookie==null){//cookie为null则内存中不含有该账户的cookie，需要新建一个
                LoggedUser+=1;
                Cookie = "User"+LoggedUser;
                CookieToAccount.put(Cookie, tmp[0]);
                AccountToCookie.put(tmp[0],Cookie);
            }
            String Content = "Login succeed,welcome "+tmp[0]+" !";
            String response = "";
            response+="HTTP/1.1 200 OK\r\n";
            response+="Date: "+getServerTime()+"\r\n";
            response+="Set-Cookie: "+Cookie+"\r\n";
            byte[] Contentbytes = Content.getBytes();
            response+="Content-Type: text/plain\r\n";
            response+="Content-Length: "+Contentbytes.length+"\r\n";
            response+="\r\n";
            response+=Content;
            return response.getBytes();
        }
    }

    String[] parseUsernameAndPassword(byte[]body){//从POST方法的请求内容中解析出账户名和密码
        String s = new String(body);
        s=Decoder(s);
        if(s==null){
            return null;
        }
        String[] splits = s.split("&");
        String[] re = new String[2];
        if(splits.length!=2) {
            return null;
        }else{
            String[] s1 = splits[0].split("=");
            String[] s2 = splits[1].split("=");
            if(s1[0].toLowerCase().equals("username")&&s2[0].toLowerCase().equals("password")) {
                re[0]=s1[1];
                re[1]=s2[1];
                return re;
            } else if(s2[0].toLowerCase().equals("username")&&s1[0].toLowerCase().equals("password")){
                re[0]=s2[1];
                re[1]=s1[1];
                return re;
            }else {
                return null;
            }
        }
    }

    String Decoder(String s) {//解码器，解析utf-8格式的内容
        try {
            return URLDecoder.decode(s, "UTF-8");
        }catch (UnsupportedEncodingException e){
            e.printStackTrace();
            return null;
        }
    }
}
