const API_BASE = 'http://localhost:8000/api/v1';

document.addEventListener('DOMContentLoaded', () => {
    loadPendingReports();
});

async function loadPendingReports() {
    const grid = document.getElementById('reports-grid');
    const loader = document.getElementById('loader');
    const emptyState = document.getElementById('empty-state');
    const pendingCount = document.getElementById('pending-count');

    // Reset UI
    grid.innerHTML = '';
    grid.classList.add('hidden');
    emptyState.classList.add('hidden');
    loader.classList.remove('hidden');

    try {
        const response = await fetch(`${API_BASE}/admin/reports/pending`);

        if (!response.ok) {
            throw new Error('Failed to fetch reports');
        }

        const data = await response.json();
        const reports = data.reports || [];

        pendingCount.textContent = data.count || reports.length;

        loader.classList.add('hidden');

        if (reports.length === 0) {
            emptyState.classList.remove('hidden');
        } else {
            renderReports(reports);
            grid.classList.remove('hidden');
        }

    } catch (error) {
        console.error('Error fetching reports:', error);
        loader.classList.add('hidden');
        showToast('Error connecting to intelligence core', 'error');
    }
}

function renderReports(reports) {
    const grid = document.getElementById('reports-grid');

    reports.forEach(report => {
        const card = document.createElement('div');
        card.className = 'report-card';
        card.id = `report-${report.report_id}`;

        const createdDate = new Date(report.created_at).toLocaleString();
        const severityIcons = '🔥'.repeat(report.severity || 1);

        // Ensure image URL exists, otherwise fallback
        const imageUrl = report.image_url || 'https://via.placeholder.com/400x300?text=No+Image';
        const aiReasoning = report.ai_reasoning || 'AI confidence too low. Manual review strictly required.';
        const type = String(report.incident_type || 'Unknown').replace('_', ' ').toUpperCase();

        card.innerHTML = `
            <div class="card-image-wrapper">
                <img src="${imageUrl}" alt="Hazard Scene" onerror="this.src='https://via.placeholder.com/400x300?text=Image+Load+Failed'">
                <div class="confidence-badge">
                    <i class="fa-solid fa-robot"></i> ${Math.round(report.verification_confidence || 0)}%
                </div>
                <div class="type-badge">${type}</div>
            </div>
            
            <div class="card-body">
                <div class="card-meta">
                    <span><i class="fa-regular fa-clock"></i> ${createdDate}</span>
                    <span>Sev: ${severityIcons}</span>
                </div>
                
                <p class="card-description">
                    ${report.description || 'No description provided by the reporter.'}
                </p>

                <div class="ai-reasoning">
                    <i class="fa-solid fa-microchip"></i>
                    <strong>AI Note:</strong> ${aiReasoning}
                </div>

                <div class="card-actions">
                    <button class="btn-reject" onclick="resolveReport('${report.report_id}', 'reject')">
                        <i class="fa-solid fa-xmark"></i> Reject
                    </button>
                    <button class="btn-approve" onclick="resolveReport('${report.report_id}', 'approve')">
                        <i class="fa-solid fa-check"></i> Approve
                    </button>
                </div>
            </div>
        `;

        grid.appendChild(card);
    });
}

async function resolveReport(reportId, action) {
    // Optimistic UI Removal
    const card = document.getElementById(`report-${reportId}`);
    if (card) {
        card.style.opacity = '0.5';
        card.style.pointerEvents = 'none';
    }

    try {
        const formData = new URLSearchParams();
        formData.append('action', action);
        formData.append('admin_id', 'admin_dashboard');

        const response = await fetch(`${API_BASE}/admin/reports/${reportId}/resolve`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData.toString()
        });

        if (!response.ok) {
            throw new Error(`Failed to ${action} report`);
        }

        // Remove from UI completely
        if (card) card.remove();

        // Update Count
        const countElement = document.getElementById('pending-count');
        let currentCount = parseInt(countElement.textContent) || 0;
        countElement.textContent = Math.max(0, currentCount - 1);

        // Check if empty
        if (currentCount - 1 <= 0) {
            document.getElementById('reports-grid').classList.add('hidden');
            document.getElementById('empty-state').classList.remove('hidden');
        }

        showToast(`Report ${action === 'approve' ? 'Verified' : 'Rejected'} successfully`, 'success');

    } catch (error) {
        console.error(error);
        if (card) {
            card.style.opacity = '1';
            card.style.pointerEvents = 'auto';
        }
        showToast(`Failed to ${action} report`, 'error');
    }
}

function showToast(message, type) {
    const toast = document.getElementById('toast');
    const toastMessage = document.getElementById('toast-message');
    const icon = document.getElementById('toast-icon');

    toastMessage.textContent = message;

    // Reset classes
    toast.className = 'toast';
    toast.classList.add(type);

    if (type === 'success') {
        icon.className = 'fa-solid fa-circle-check';
    } else {
        icon.className = 'fa-solid fa-triangle-exclamation';
    }

    // Show toast
    toast.classList.remove('hidden');

    // Hide after 3s
    setTimeout(() => {
        toast.classList.add('hidden');
    }, 3000);
}
