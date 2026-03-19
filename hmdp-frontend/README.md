# HMDP 前端项目

周边优速通前端项目 - 前后端分离版本

## 项目结构

```
hmdp-frontend/
├── css/              # 样式文件
├── js/               # JavaScript文件
├── imgs/             # 图片资源
├── *.html            # 页面文件
├── package.json      # npm配置
└── README.md         # 项目说明
```

## 技术栈

- Vue.js 2.x
- Element UI
- Axios
- 纯静态HTML页面

## 开发环境

```bash
# 安装依赖
npm install

# 启动开发服务器（端口3000）
npm run dev
```

## 生产环境

### 方式1：使用 Nginx（推荐）

将项目文件夹复制到 Nginx 的 html 目录：

```bash
# Windows
xcopy /E /I hmdp-frontend "C:\nginx\html\hmdp"

# 或 Linux/Mac
cp -r hmdp-frontend /usr/share/nginx/html/hmdp
```

Nginx 配置：

```nginx
server {
    listen 8080;
    server_name localhost;
    
    location / {
        root html/hmdp;
        index index.html;
    }
    
    location /api {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 方式2：使用 http-server

```bash
npm install -g http-server
http-server -p 8080
```

## API 接口配置

后端 API 地址在 `js/common.js` 中配置：

```javascript
// 开发环境
const BASE_URL = 'http://localhost:8081';

// 生产环境（通过Nginx代理）
const BASE_URL = '';
```

## 注意事项

1. 确保后端服务已启动（端口8081）
2. 确保Redis服务已启动
3. 确保MySQL数据库已配置

## 后端项目

后端项目地址：`../src/`

启动后端：
```bash
cd ..
mvn spring-boot:run
```
