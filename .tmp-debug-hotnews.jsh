import com.hmdp.service.impl.CityHotNewsServiceImpl;
import java.lang.reflect.*;
CityHotNewsServiceImpl svc = new CityHotNewsServiceImpl();
Method m = CityHotNewsServiceImpl.class.getDeclaredMethod("resolveDirectArticleUrl", String.class);
m.setAccessible(true);
Object v = m.invoke(svc, "https://news.google.com/rss/articles/CBMiiAFBVV95cUxOZnhWNzhkOW9DZDVIMmJSYjQ4eTdwMGh6dmJYQm5oUTdFd0RYdHdKVkRVOU55RzJXNU9XRkh4YnJwUExRVDl0bXFiVnUyUHNxU0Z6cDBRRXgxRFR2eHpqd0F4NDZJYkxkdjZabmNMTXVnS3czVUd0bUxFWkxCUHlxLUJ6Q2JHMXl0?oc=5");
System.out.println(v);
Method m2 = CityHotNewsServiceImpl.class.getDeclaredMethod("extractPreviewImageFromArticleUrl", String.class);
m2.setAccessible(true);
Object img = m2.invoke(svc, "https://m.sohu.com/a/1014878642_392415?scm=10001.325_13-325_13.0.0-0-0-0-0.5_1334");
System.out.println(img);
/exit
