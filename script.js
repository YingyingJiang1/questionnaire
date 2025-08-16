function getQueryParam(name) {
  const urlParams = new URLSearchParams(window.location.search);
  return urlParams.get(name);
}

// 从 URL 获取 taskid 作为 taskName
const taskName = getQueryParam('taskid') || 'task1';
const view = getQueryParam('view') || '1';

// 修改标题显示：首字母大写 + Q + view
document.getElementById('task-title').innerText =
  taskName.toString()[0].toUpperCase() + taskName.toString().slice(1) + ": Q" + view;

const filesViewMap = {
  '1': ['Target.java', 'A.java', 'B.java'],
  '2': ['Target.java', 'B.java', 'C.java', 'D.java', 'E.java', 'F.java']
};
const files = filesViewMap[view];
const container = document.getElementById('code-container');

async function loadFile(task, filename) {
  try {
    const res = await fetch(`code-materials/${task}/${filename}`);
    if (!res.ok) return `Error loading ${filename}`;
    return await res.text();
  } catch (e) {
    return `Error loading ${filename}`;
  }
}

async function init() {
  container.innerHTML = '';
  const contents = await Promise.all(files.map(f => loadFile(taskName, f)));

  contents.forEach((code, index) => {
    const panel = document.createElement('div');
    panel.className = 'code-panel';

    const header = document.createElement('div');
    header.className = 'code-header';
    if (files[index].startsWith('Target')) header.classList.add('target');
    header.innerText = files[index].replace('.java','');
    panel.appendChild(header);

    const editorDiv = document.createElement('div');
    editorDiv.className = 'code-editor';
    panel.appendChild(editorDiv);

    container.appendChild(panel);

    // 初始化 Ace Editor
    const editor = ace.edit(editorDiv);
    editor.setTheme("ace/theme/monokai");
    editor.session.setMode("ace/mode/java");
    editor.setValue(code, -1);
    editor.setOptions({
      readOnly: true,
      highlightActiveLine: false,
      highlightGutterLine: false,
      showPrintMargin: false,
      showInvisibles: true,  // 显示空格和制表符
      useWorker: false,
      fontSize: "14pt",
    });
  });

  // 初始化 Split.js，可拖动宽度
  Split(Array.from(container.children), { sizes: Array(files.length).fill(100/files.length), gutterSize: 8 });
}

init();
