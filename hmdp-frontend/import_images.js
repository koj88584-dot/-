const fs = require('fs');
const path = require('path');

const sourceDir = 'C:\\Users\\20878\\.gemini\\antigravity\\brain\\d8e31b8f-9723-4431-94be-a02df0338a7d';
const destDir = path.join(__dirname, 'imgs', 'cities');

// 确保目标文件夹存在
if (!fs.existsSync(destDir)) {
  fs.mkdirSync(destDir, { recursive: true });
}

// 城市拼音与中文名映射表
const cityMap = {
  "beijing": "北京",
  "shanghai": "上海",
  "guangzhou": "广州",
  "shenzhen": "深圳",
  "hangzhou": "杭州",
  "nanjing": "南京",
  "chengdu": "成都",
  "wuhan": "武汉",
  "xian": "西安",
  "chongqing": "重庆",
  "changsha": "长沙",
  "tianjin": "天津"
};

const files = fs.readdirSync(sourceDir);
let copiedCount = 0;

Object.keys(cityMap).forEach(cityPinyin => {
  const cityName = cityMap[cityPinyin];
  // 查找符合该城市的生成图片（获取最新的一张）
  const matchingFiles = files.filter(f => f.startsWith(`${cityPinyin}_cityscape_`) && f.endsWith('.png'));
  
  if (matchingFiles.length > 0) {
    // 排序取最新的
    matchingFiles.sort();
    const fileToCopy = matchingFiles[matchingFiles.length - 1];
    
    const srcPath = path.join(sourceDir, fileToCopy);
    // 重命名为 中文名.png，方便前端代码直接按照城市名称匹配
    const destPath = path.join(destDir, `${cityName}.png`);
    
    try {
      fs.copyFileSync(srcPath, destPath);
      console.log(`✅ 成功导入: ${cityName} 的城市配图`);
      copiedCount++;
    } catch (e) {
      console.error(`❌ 导入 ${cityName} 失败:`, e.message);
    }
  }
});

console.log(`\n🎉 图片导入完成！共导入 ${copiedCount} 张图片。`);
console.log(`现在你可以回到浏览器刷新页面，即可看到全新的高级本地图片！`);
