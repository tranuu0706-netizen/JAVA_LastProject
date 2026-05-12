// Tab Switching Logic
document.querySelectorAll('.nav-item').forEach(item => {
    item.addEventListener('click', (e) => {
        // Remove active from all navs
        document.querySelectorAll('.nav-item').forEach(nav => nav.classList.remove('active'));
        // Add active to clicked nav
        e.currentTarget.classList.add('active');
        
        // Hide all panels
        document.querySelectorAll('.panel').forEach(panel => panel.classList.remove('active'));
        // Show target panel
        const targetId = e.currentTarget.getAttribute('data-target');
        document.getElementById(targetId).classList.add('active');

        // Load data based on tab
        if (targetId === 'dashboard') loadDashboardStats();
        if (targetId === 'accounts') loadAccounts();
        if (targetId === 'crawl') loadCrawlPanel();
        if (targetId === 'submissions') loadSubmissions();
        if (targetId === 'analysis') loadAnalyses();
        if (targetId === 'evaluation') loadEvaluations();
    });
});

// Load Dashboard Stats
async function loadDashboardStats() {
    try {
        const res = await fetch('/api/stats');
        const data = await res.json();
        document.getElementById('stat-accounts').textContent = data.totalAccounts;
        document.getElementById('stat-submissions').textContent = data.totalSubmissions;
        document.getElementById('stat-analyses').textContent = data.totalAnalyses;
    } catch (err) {
        console.error('Error loading stats', err);
    }
}

// Load Accounts
async function loadAccounts() {
    try {
        const res = await fetch('/api/accounts');
        const data = await res.json();
        const tbody = document.getElementById('accounts-table-body');
        tbody.innerHTML = '';
        if (!data || data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color: var(--text-muted); padding: 40px;">Chưa có tài khoản nào.</td></tr>';
            return;
        }
        data.forEach(acc => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td class="account-id-cell">${acc.id}</td>
                <td title="${escapeHtml(acc.username)}"><strong class="text-ellipsis">${escapeHtml(acc.username)}</strong></td>
                <td><span class="badge badge-primary">${escapeHtml(acc.platform)}</span></td>
                <td title="${escapeHtml(acc.displayName || '')}"><span class="text-ellipsis">${escapeHtml(acc.displayName || '')}</span></td>
                <td><span class="badge badge-success">${acc.submissionCount || 0}</span></td>
                <td class="account-actions-cell">
                    <div class="account-actions">
                        <button class="btn btn-primary btn-sm" onclick="crawlAccount(${acc.id})"><i class="fa-solid fa-spider"></i> Crawl</button>
                        <button class="btn btn-success btn-sm" onclick="analyzeAccount(${acc.id})"><i class="fa-solid fa-magnifying-glass-chart"></i> Phân tích</button>
                        <button class="btn btn-success btn-sm" onclick="evaluateAccount(${acc.id})"><i class="fa-solid fa-star"></i> Đánh giá</button>
                        <button class="btn btn-danger btn-sm" onclick="deleteAccount(${acc.id})"><i class="fa-solid fa-trash"></i> Xóa</button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        console.error('Error loading accounts', err);
    }
}

// Add Account
document.getElementById('add-account-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const submitButton = e.target.querySelector('button[type="submit"]');
    const payload = {
        username: document.getElementById('acc-username').value,
        platform: document.getElementById('acc-platform').value,
        displayName: document.getElementById('acc-displayname').value
    };
    
    try {
        submitButton.disabled = true;
        submitButton.textContent = 'Đang kiểm tra...';
        const res = await fetch('/api/accounts', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            alert('Thêm tài khoản thành công!');
            e.target.reset();
            loadAccounts();
        } else {
            const data = await res.json().catch(() => ({}));
            alert(data.error || 'Không thể thêm tài khoản.');
        }
    } catch (err) {
        console.error('Add account error', err);
        alert('Lỗi kết nối khi kiểm tra/thêm tài khoản.');
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = 'Thêm';
    }
});

// Crawl Account
async function crawlAccount(id) {
    if (!confirm('Bắt đầu crawl dữ liệu cho tài khoản này?')) return;
    try {
        const res = await fetch(`/api/crawl/${id}`, { method: 'POST' });
        if (res.ok) alert('Đang chạy tiến trình Crawl (chạy ngầm)');
        else if (res.status === 409) alert('Đang có tiến trình crawl khác chạy. Vui lòng chờ hoàn tất.');
    } catch (err) {
        console.error('Crawl error', err);
    }
}

// Delete Account
async function deleteAccount(id) {
    if (!confirm('Xóa nick này? Toàn bộ submissions, phân tích AI và đánh giá liên quan cũng sẽ bị xóa.')) return;
    try {
        const res = await fetch(`/api/accounts/${id}`, { method: 'DELETE' });
        if (res.ok) {
            alert('Đã xóa tài khoản.');
            loadAccounts();
            loadDashboardStats();
        } else if (res.status === 409) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || 'Không thể xóa khi tiến trình đang chạy.');
        } else {
            const data = await res.json().catch(() => ({}));
            alert(data.error || 'Không thể xóa tài khoản.');
        }
    } catch (err) {
        console.error('Delete account error', err);
        alert('Lỗi kết nối khi xóa tài khoản.');
    }
}

// Crawl Panel
async function loadCrawlPanel() {
    await Promise.all([
        loadSchedulerStatus(),
        loadSchedulerConfig(),
        loadCrawlJobs()
    ]);
}

async function loadSchedulerStatus() {
    try {
        const res = await fetch('/api/scheduler/status');
        const data = await res.json();
        renderSchedulerStatus(data);
    } catch (err) {
        console.error('Scheduler status error', err);
    }
}

function renderSchedulerStatus(data) {
    const schedulerStatus = document.getElementById('scheduler-status');
    const crawlStatus = document.getElementById('crawl-running-status');
    const plan = document.getElementById('scheduler-plan');
    if (!schedulerStatus || !crawlStatus || !plan) return;

    schedulerStatus.className = `badge ${data.schedulerRunning ? 'badge-success' : 'badge-warning'}`;
    schedulerStatus.textContent = data.schedulerRunning ? 'Đang bật' : 'Đã tắt';

    crawlStatus.className = `badge ${data.crawlRunning ? 'badge-warning' : 'badge-success'}`;
    crawlStatus.textContent = data.crawlRunning ? 'Đang crawl' : 'Rảnh';

    plan.textContent = `Mỗi ${data.intervalHours || 24} giờ, bắt đầu ${data.startTime || '02:00'}`;
}

async function loadSchedulerConfig() {
    try {
        const res = await fetch('/api/config/scheduler');
        const data = await res.json();
        document.getElementById('scheduler-interval').value = data.intervalHours || 24;
        document.getElementById('scheduler-start-time').value = data.startTime || '02:00';
    } catch (err) {
        console.error('Scheduler config error', err);
    }
}

document.getElementById('scheduler-config-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const message = document.getElementById('crawl-status-message');
    const payload = {
        intervalHours: Number(document.getElementById('scheduler-interval').value),
        startTime: document.getElementById('scheduler-start-time').value
    };

    try {
        const res = await fetch('/api/config/scheduler', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            const data = await res.json();
            renderSchedulerStatus(data);
            message.textContent = 'Đã lưu lịch crawl.';
        } else {
            const data = await res.json();
            message.textContent = data.error || 'Không thể lưu lịch crawl.';
        }
    } catch (err) {
        message.textContent = 'Lỗi kết nối khi lưu lịch crawl.';
        console.error('Save scheduler config error', err);
    }
});

async function startCrawlAll() {
    if (!confirm('Bắt đầu crawl tất cả tài khoản đang active?')) return;
    const message = document.getElementById('crawl-status-message');
    try {
        const res = await fetch('/api/crawl-all', { method: 'POST' });
        if (res.ok) {
            message.textContent = 'Đang chạy crawl tất cả tài khoản.';
            loadSchedulerStatus();
        } else if (res.status === 409) {
            message.textContent = 'Đang có tiến trình crawl khác chạy.';
        } else {
            message.textContent = 'Không thể bắt đầu crawl.';
        }
    } catch (err) {
        message.textContent = 'Lỗi kết nối khi bắt đầu crawl.';
        console.error('Crawl all error', err);
    }
}

async function schedulerAction(action) {
    const message = document.getElementById('crawl-status-message');
    try {
        const res = await fetch(`/api/scheduler/${action}`, { method: 'POST' });
        const data = await res.json();
        if (res.ok) {
            renderSchedulerStatus(data);
            message.textContent = 'Đã cập nhật trạng thái scheduler.';
        } else {
            message.textContent = data.error || 'Không thể cập nhật scheduler.';
        }
    } catch (err) {
        message.textContent = 'Lỗi kết nối scheduler.';
        console.error('Scheduler action error', err);
    }
}

async function loadCrawlJobs() {
    try {
        const res = await fetch('/api/crawl/jobs');
        const data = await res.json();
        const tbody = document.getElementById('crawl-jobs-table-body');
        tbody.innerHTML = '';
        if (!data || data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color: var(--text-muted); padding: 40px;">Chưa có lịch sử crawl.</td></tr>';
            return;
        }

        data.forEach(job => {
            let statusClass = 'badge-primary';
            if (job.status === 'SUCCESS') statusClass = 'badge-success';
            else if (job.status === 'FAILED') statusClass = 'badge-danger';
            else if (job.status === 'PARTIAL') statusClass = 'badge-warning';

            const errorText = truncateText(job.errorLog || '', 120);
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${job.id || ''}</td>
                <td>${parseDateTime(job.startedAt)}</td>
                <td>${parseDateTime(job.finishedAt)}</td>
                <td><span class="badge ${statusClass}">${job.status || ''}</span></td>
                <td>${job.accountsProcessed || 0}</td>
                <td>${job.submissionsCrawled || 0}</td>
                <td title="${job.errorLog || ''}">${errorText || '-'}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        console.error('Load crawl jobs error', err);
    }
}

// Analyze one account
async function analyzeAccount(id) {
    if (!confirm('Bắt đầu phân tích AI các bài chưa phân tích của nick này?')) return;
    try {
        const res = await fetch(`/api/accounts/${id}/analyze`, { method: 'POST' });
        if (res.ok) alert('Đang phân tích code của nick này (chạy ngầm)');
        else if (res.status === 409) alert('Đang có tiến trình phân tích khác chạy. Vui lòng chờ hoàn tất.');
        else if (res.status === 404) alert('Không tìm thấy tài khoản.');
        else alert('Không thể bắt đầu phân tích.');
    } catch (err) {
        console.error('Analyze account error', err);
    }
}

// Evaluate Account
async function evaluateAccount(id) {
    if (!confirm('Bắt đầu đánh giá AI cho tài khoản này?')) return;
    try {
        const res = await fetch(`/api/evaluate/${id}`, { method: 'POST' });
        if (res.ok) alert('Đang chạy tiến trình Đánh giá (chạy ngầm)');
        else if (res.status === 409) alert('Đang có tiến trình đánh giá khác chạy. Vui lòng chờ hoàn tất.');
    } catch (err) {
        console.error('Eval error', err);
    }
}

// Evaluate All Accounts
async function evaluateAll() {
    if (!confirm('Đánh giá tất cả tài khoản?')) return;
    try {
        const res = await fetch('/api/evaluate-all', { method: 'POST' });
        if (res.ok) alert('Đang đánh giá tất cả tài khoản (chạy ngầm)');
        else if (res.status === 409) alert('Đang có tiến trình đánh giá khác chạy. Vui lòng chờ hoàn tất.');
    } catch (err) {
        console.error('Eval all error', err);
    }
}

// Helper: parse date from either ISO string or nested object format
function parseDateTime(dt) {
    if (!dt) return '';
    try {
        // ISO string format from JavaTimeModule: "2024-01-15T10:30:00"
        if (typeof dt === 'string') {
            return new Date(dt).toLocaleString('vi-VN');
        }
        // Nested object format (legacy): {date: {year, month, day}, time: {hour, minute}}
        if (dt.date && dt.time) {
            return new Date(dt.date.year, dt.date.month - 1, dt.date.day, dt.time.hour, dt.time.minute).toLocaleString('vi-VN');
        }
        // Array format [year, month, day, hour, minute, second]
        if (Array.isArray(dt)) {
            return new Date(dt[0], dt[1] - 1, dt[2], dt[3] || 0, dt[4] || 0).toLocaleString('vi-VN');
        }
    } catch (e) {
        console.warn('Date parse error:', e);
    }
    return '';
}

// Helper: parse JSON string safely
function parseJsonArray(str) {
    if (!str) return [];
    if (Array.isArray(str)) return str;
    try {
        const arr = JSON.parse(str);
        return Array.isArray(arr) ? arr : [];
    } catch (e) {
        return [];
    }
}

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// Helper: difficulty badge color
function difficultyBadge(level) {
    if (!level) return '<span class="badge">N/A</span>';
    const colors = {
        'BEGINNER': 'badge-success',
        'EASY': 'badge-success',
        'MEDIUM': 'badge-warning',
        'HARD': 'badge-danger',
        'EXPERT': 'badge-danger'
    };
    return `<span class="badge ${colors[level] || 'badge-primary'}">${level}</span>`;
}

function truncateText(value, maxLength) {
    if (!value) return '';
    const text = String(value);
    return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
}

// Load Submissions
async function loadSubmissions() {
    try {
        const res = await fetch('/api/submissions');
        const data = await res.json();
        const tbody = document.getElementById('submissions-table-body');
        tbody.innerHTML = '';
        if (!data || data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color: var(--text-muted); padding: 40px;">Chưa có submissions nào. Hãy thêm tài khoản và crawl dữ liệu.</td></tr>';
            return;
        }
        data.forEach(sub => {
            const verdictClass = sub.verdict === 'OK' || sub.verdict === 'Accepted' || sub.verdict === 'AC' ? 'badge-success' : 'badge-danger';
            const dateStr = parseDateTime(sub.submittedAt);
            const coder = sub.displayName || sub.username || `Account #${sub.accountId || ''}`;
            
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${sub.submissionId || ''}</td>
                <td><strong>${coder}</strong><br><small style="color:var(--text-muted)">${sub.username || ''}</small></td>
                <td>${sub.problemName || sub.problemId || ''}</td>
                <td>${sub.platform || ''}</td>
                <td><span class="badge ${verdictClass}">${sub.verdict || ''}</span></td>
                <td>${sub.language || ''}</td>
                <td>${dateStr}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        console.error('Error loading submissions', err);
    }
}

// Start Analysis
async function startAnalysis() {
    const statusDiv = document.getElementById('analysis-status');
    statusDiv.textContent = 'Đang gửi yêu cầu phân tích...';
    try {
        const res = await fetch('/api/analyze', { method: 'POST' });
        if (res.ok) {
            statusDiv.textContent = '✅ Tiến trình phân tích đang chạy ngầm. Nhấn "Làm mới" sau vài phút để xem kết quả.';
        } else if (res.status === 409) {
            statusDiv.textContent = '⏳ Tiến trình phân tích đang chạy. Vui lòng chờ hoàn tất rồi thử lại.';
        } else {
            statusDiv.textContent = '❌ Không thể bắt đầu phân tích.';
        }
    } catch (err) {
        statusDiv.textContent = '❌ Lỗi kết nối';
        console.error('Analysis error', err);
    }
}

// Load AI Analyses
async function loadAnalyses() {
    try {
        const res = await fetch('/api/analyses');
        const data = await res.json();
        const tbody = document.getElementById('analyses-table-body');
        tbody.innerHTML = '';
        if (!data || data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="10" style="text-align:center; color: var(--text-muted); padding: 40px;">Chưa có kết quả phân tích. Nhấn "Bắt đầu phân tích" để AI phân tích các bài tập.</td></tr>';
            return;
        }
        data.forEach(a => {
            const ds = parseJsonArray(a.dataStructures);
            const algos = parseJsonArray(a.algorithms);
            const dsHtml = ds.map(d => `<span class="badge badge-primary" style="margin:2px;font-size:11px;">${d}</span>`).join('');
            const algoHtml = algos.map(al => `<span class="badge badge-success" style="margin:2px;font-size:11px;">${al}</span>`).join('');
            
            // AI Score color
            let aiScoreClass = 'badge-success';
            if (a.aiUsageScore > 60) aiScoreClass = 'badge-danger';
            else if (a.aiUsageScore > 30) aiScoreClass = 'badge-warning';

            // Quality score color
            let qualityClass = 'badge-danger';
            if (a.codeQualityScore >= 70) qualityClass = 'badge-success';
            else if (a.codeQualityScore >= 40) qualityClass = 'badge-warning';

            const summary = a.analysisSummary ? (a.analysisSummary.length > 80 ? a.analysisSummary.substring(0, 80) + '...' : a.analysisSummary) : '';
            const aiReason = truncateText(a.aiUsageReason || '', 100);
            const coder = a.displayName || a.username || `Account #${a.accountId || ''}`;

            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${coder}</strong><br><small style="color:var(--text-muted)">${a.username || ''}</small></td>
                <td><strong>${a.problemName || a.problemId || ''}</strong><br><small style="color:var(--text-muted)">${a.language || ''}</small></td>
                <td>${dsHtml || '<span style="color:var(--text-muted)">-</span>'}</td>
                <td>${algoHtml || '<span style="color:var(--text-muted)">-</span>'}</td>
                <td><small>${a.complexityTime || 'Unknown'}</small><br><small style="color:var(--text-muted)">${a.complexitySpace || 'Unknown'}</small></td>
                <td>${difficultyBadge(a.difficultyLevel)}</td>
                <td><span class="badge ${aiScoreClass}">${a.aiUsageScore}/100</span></td>
                <td title="${a.aiUsageReason || ''}" style="max-width:180px;font-size:12px;">${aiReason || '-'}</td>
                <td><span class="badge ${qualityClass}">${a.codeQualityScore}/100</span></td>
                <td style="max-width:200px;font-size:12px;">${summary}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        console.error('Error loading analyses', err);
    }
}

// Load Evaluations
async function loadEvaluations() {
    try {
        const res = await fetch('/api/evaluations');
        const data = await res.json();
        const tbody = document.getElementById('evaluations-table-body');
        tbody.innerHTML = '';
        if (!data || data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="10" style="text-align:center; color: var(--text-muted); padding: 40px;">Chưa có đánh giá nào. Hãy phân tích AI trước rồi đánh giá.</td></tr>';
            return;
        }
        data.forEach(ev => {
            let aiBadge = 'badge-success';
            if (ev.aiUsageLevel && ev.aiUsageLevel.includes('HIGH')) aiBadge = 'badge-danger';
            else if (ev.aiUsageLevel && ev.aiUsageLevel.includes('MEDIUM')) aiBadge = 'badge-warning';

            const dateStr = parseDateTime(ev.evaluatedAt);
            const coder = ev.displayName || ev.username || `Account #${ev.accountId || ''}`;
            const strengths = truncateText(ev.strengths || '', 120);
            const weaknesses = truncateText(ev.weaknesses || '', 120);
            const recommendation = truncateText(ev.recommendation || '', 140);

            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${coder}</strong><br><small style="color:var(--text-muted)">${ev.username || ''} ${ev.platform ? '(' + ev.platform + ')' : ''}</small></td>
                <td><span class="badge badge-primary">${ev.overallLevel || ''}</span></td>
                <td>${ev.dsScore || 0}</td>
                <td>${ev.algoScore || 0}</td>
                <td><span class="badge ${aiBadge}">${ev.aiUsageLevel || ''}</span><br><small style="color:var(--text-muted)">${ev.avgAiUsageScore || 0}/100</small></td>
                <td>${ev.totalSubmissionsAnalyzed || 0}</td>
                <td title="${ev.strengths || ''}" style="max-width:180px;font-size:12px;">${strengths || '-'}</td>
                <td title="${ev.weaknesses || ''}" style="max-width:180px;font-size:12px;">${weaknesses || '-'}</td>
                <td title="${ev.recommendation || ''}" style="max-width:220px;font-size:12px;">${recommendation || '-'}</td>
                <td>${dateStr}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        console.error('Error loading evaluations', err);
    }
}

// Initial load
loadDashboardStats();
