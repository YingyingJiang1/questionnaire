import os
import shutil
import json

# 原始目录
root_dir = "code-materials"
# 合并后的目录前缀
task_prefix = "task"

# 期望的文件名集合
required_files = {"Target.java", "A.java", "B.java", "C.java", "D.java", "E.java", "F.java"}

# 收集符合条件的最深层目录
valid_dirs = []

for dirpath, dirnames, filenames in os.walk(root_dir):
    filenames_set = set(filenames)
    if required_files.issubset(filenames_set):
        valid_dirs.append(dirpath)

# 映射字典
task_mapping = {}

# 每个符合条件的目录对应一个 taskN
for i, d in enumerate(valid_dirs):
    task_name = f"{task_prefix}{i+1}"
    task_path = os.path.join(root_dir, task_name)
    os.makedirs(task_path, exist_ok=True)

    # 保存映射
    task_mapping[task_name] = d

    for file in required_files:
        src_file = os.path.join(d, file)
        dst_file = os.path.join(task_path, file)
        # 文件名冲突处理
        if os.path.exists(dst_file):
            base, ext = os.path.splitext(file)
            dst_file = os.path.join(task_path, f"{base}_{os.path.basename(d)}{ext}")
        shutil.copy2(src_file, dst_file)

# 导出映射文件
mapping_file = os.path.join(root_dir, "task_mapping.json")
with open(mapping_file, "w", encoding="utf-8") as f:
    json.dump(task_mapping, f, indent=4, ensure_ascii=False)

print(f"转换完成！仅处理包含完整 Target + A-F 的目录。映射已保存到 {mapping_file}")
