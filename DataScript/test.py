import re

# code = r"'D1(北京-沈阳南)'"
# # 或者 code = "xxx'D1(北京-沈阳南)'yyy'G234(上海-广州南)'zzz"

# # 匹配：单引号 + 列车号（字母+数字） + (任意非右括号字符) + 单引号
# result = re.findall(r"'([^\(]+)\(([^-]+)-([^\)]+)\)'", code)

# print(result)
# # 输出: ['D1(北京-沈阳南)']
from crawl_train_station import fetch_train_detail

fetch_train_detail('5l0000G47300','AOH','CXW','2025-12-11')