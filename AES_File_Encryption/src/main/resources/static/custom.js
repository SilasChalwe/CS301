
    // API base URL
    const api = 'http://localhost:8080/api';

    // Navigation
    document.querySelectorAll('.nav-item').forEach(item => {
    item.addEventListener('click', function() {
        // Update active nav item
        document.querySelectorAll('.nav-item').forEach(nav => nav.classList.remove('active'));
        this.classList.add('active');

        // Show corresponding section
        const target = this.getAttribute('data-target');
        document.querySelectorAll('.content-section').forEach(section => {
            section.classList.remove('active');
        });
        document.getElementById(`${target}-section`).classList.add('active');

        // Load data when switching to specific sections
        if(target === 'files') {
            listFiles();
        }
    });
});

    // Mobile menu toggle
    document.getElementById('menuToggle').addEventListener('click', function() {
    document.getElementById('sidebar').classList.toggle('active');
});

    // Helper functions
    function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

    function formatDate(dateString) {
    const options = { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' };
    return new Date(dateString).toLocaleDateString(undefined, options);
}

    function getFileType(filename) {
    const ext = filename.split('.').pop().toLowerCase();
    const types = {
    'pdf': 'PDF Document',
    'doc': 'Word Document',
    'docx': 'Word Document',
    'xls': 'Excel Spreadsheet',
    'xlsx': 'Excel Spreadsheet',
    'jpg': 'JPEG Image',
    'jpeg': 'JPEG Image',
    'png': 'PNG Image',
    'txt': 'Text File',
    'enc': 'Encrypted File',
    'key': 'Encryption Key'
};
    return types[ext] || ext.toUpperCase() + ' File';
}

    // Show loading spinner
    function showSpinner(id) {
    document.getElementById(id).style.display = 'block';
}

    // Hide loading spinner
    function hideSpinner(id) {
    document.getElementById(id).style.display = 'none';
}

    // API functions
    function pingDevice() {
    showSpinner('pingSpinner');
    showSpinner('pingDeviceSpinner');
    document.getElementById('pingLog').textContent = '';
    document.getElementById('pingLogDevice').textContent = '';

    fetch(`${api}/ping`)
    .then(handleResponse)
    .then(data => {
    document.getElementById('pingLog').textContent = JSON.stringify(data, null, 2);
    document.getElementById('pingLogDevice').textContent = JSON.stringify(data, null, 2);

    // Update security status
    if(data.message.includes('ONLINE')) {
    document.getElementById('encryptionStrength').textContent = 'AES-256';
    document.getElementById('encryptionBar').style.width = '100%';
    document.getElementById('keyStatus').textContent = 'Provisioned';
    document.getElementById('keyBar').style.width = '80%';
}
})
    .catch(error => {
    document.getElementById('pingLog').textContent = 'Error: ' + error.message;
    document.getElementById('pingLogDevice').textContent = 'Error: ' + error.message;
})
    .finally(() => {
    hideSpinner('pingSpinner');
    hideSpinner('pingDeviceSpinner');
});
}

    function uploadFile() {
    const fileInput = document.getElementById('uploadFile') || document.getElementById('quickFile');
    const passwordInput = document.getElementById('uploadPassword') || document.getElementById('quickPassword');

    if (!fileInput.files.length) {
    document.getElementById('uploadLog').textContent = '⚠️ Please select a file to encrypt';
    return;
}

    showSpinner('uploadSpinner');
    showSpinner('encryptSpinner');
    document.getElementById('uploadLog').textContent = '';

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);
    formData.append("userPassword", passwordInput.value);

    fetch(`${api}/files/upload`, { method: 'POST', body: formData })
    .then(handleResponse)
    .then(data => {
    document.getElementById('uploadLog').textContent = JSON.stringify(data, null, 2);

    // If successful, update recent activity
    if(data.filename) {
    updateRecentActivity(`[${new Date().toLocaleTimeString()}] File "${fileInput.files[0].name}" encrypted successfully`);
    // Refresh file list
    listFiles();
}
})
    .catch(error => {
    document.getElementById('uploadLog').textContent = 'Error: ' + error.message;
})
    .finally(() => {
    hideSpinner('uploadSpinner');
    hideSpinner('encryptSpinner');
});
}

    function decryptFile() {
    const encryptedFile = document.getElementById('decryptFile').files[0];
    const keyFile = document.getElementById('keyFile').files[0];
    const userPassword = document.getElementById('decryptPassword').value;

    if (!encryptedFile) {
    document.getElementById('decryptLog').textContent = '⚠️ Please select a file to decrypt';
    return;
}

    showSpinner('decryptSpinner');
    document.getElementById('decryptLog').textContent = '';

    const formData = new FormData();
    formData.append("encryptedFile", encryptedFile);
    formData.append("keyFile", keyFile);
    formData.append("userPassword", userPassword);

    fetch(`${api}/files/decrypt`, { method: 'POST', body: formData })
    .then(response => {
    if (response.ok) {
    return response.blob().then(blob => {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'decrypted_' + encryptedFile.name;
    a.click();
    document.getElementById('decryptLog').textContent = '✅ File downloaded successfully.';

    // Update recent activity
    updateRecentActivity(`[${new Date().toLocaleTimeString()}] File "${encryptedFile.name}" decrypted successfully`);
});
} else {
    return response.json().then(data => {
    throw new Error(data.error || 'Decryption failed');
});
}
})
    .catch(error => {
    document.getElementById('decryptLog').textContent = 'Error: ' + error.message;
})
    .finally(() => {
    hideSpinner('decryptSpinner');
});
}

    function listFiles() {
    showSpinner('filesSpinner');
    document.getElementById('filesLog').textContent = '';

    fetch(`${api}/files`)
    .then(handleResponse)
    .then(data => {
    if(data.length > 0) {
    const tbody = document.getElementById('filesTableBody');
    tbody.innerHTML = '';

    // Get metadata for each file
    const promises = data.map(file =>
    fetch(`${api}/files/meta/${encodeURIComponent(file.filename)}`)
    .then(handleResponse)
    );

    Promise.all(promises)
    .then(metas => {
    metas.forEach(meta => {
    const row = document.createElement('tr');
    row.innerHTML = `
                                    <td>${meta.filename}</td>
                                    <td>${getFileType(meta.filename)}</td>
                                    <td>${formatFileSize(meta.size)}</td>
                                    <td>${formatDate(meta.createdAt)}</td>
                                    <td><span class="status-badge status-encrypted">Encrypted</span></td>
                                    <td>
                                        <button class="action-btn" data-filename="${meta.filename}">
                                            <i class="fas fa-unlock"></i> Decrypt
                                        </button>
                                    </td>
                                `;
    tbody.appendChild(row);
});

    // Add event listeners to decrypt buttons
    document.querySelectorAll('.action-btn').forEach(btn => {
    btn.addEventListener('click', function() {
    const filename = this.getAttribute('data-filename');
    showDecryptModal(filename);
});
});
});
} else {
    document.getElementById('filesLog').textContent = 'No encrypted files found';
}
})
    .catch(error => {
    document.getElementById('filesLog').textContent = 'Error: ' + error.message;
})
    .finally(() => {
    hideSpinner('filesSpinner');
});
}

    function fetchKey() {
    showSpinner('keySpinner');
    showSpinner('keyDeviceSpinner');
    document.getElementById('keyLog').textContent = '';
    document.getElementById('keyLogDevice').textContent = '';

    fetch(`${api}/device/key`)
    .then(handleResponse)
    .then(data => {
    document.getElementById('keyLog').textContent = JSON.stringify(data, null, 2);
    document.getElementById('keyLogDevice').textContent = JSON.stringify(data, null, 2);

    // Update security status
    if(data.keyHex) {
    document.getElementById('keyStatus').textContent = 'Provisioned';
    document.getElementById('keyBar').style.width = '80%';
}
})
    .catch(error => {
    document.getElementById('keyLog').textContent = 'Error: ' + error.message;
    document.getElementById('keyLogDevice').textContent = 'Error: ' + error.message;
})
    .finally(() => {
    hideSpinner('keySpinner');
    hideSpinner('keyDeviceSpinner');
});
}

    function displayMessage() {
    const message = document.getElementById('oledMessage').value;
    if (!message) {
    document.getElementById('oledLog').textContent = '⚠️ Please enter a message to display';
    return;
}

    showSpinner('displaySpinner');
    document.getElementById('oledLog').textContent = '';

    const formData = new URLSearchParams();
    formData.append('message', message);

    fetch(`${api}/device/display`, { method: 'POST', body: formData })
    .then(handleResponse)
    .then(data => {
    document.getElementById('oledLog').textContent = JSON.stringify(data, null, 2);

    // Update recent activity
    updateRecentActivity(`[${new Date().toLocaleTimeString()}] Displayed message on device: "${message}"`);
})
    .catch(error => {
    document.getElementById('oledLog').textContent = 'Error: ' + error.message;
})
    .finally(() => {
    hideSpinner('displaySpinner');
});
}

    function provisionKey() {
    const keyHex = document.getElementById('provisionKey').value;

    showSpinner('keyDeviceSpinner');
    document.getElementById('keyLogDevice').textContent = '';

    const formData = new URLSearchParams();
    if (keyHex) formData.append('keyHex', keyHex);

    fetch(`${api}/device/provision`, { method: 'POST', body: formData })
    .then(handleResponse)
    .then(data => {
    document.getElementById('keyLogDevice').textContent = JSON.stringify(data, null, 2);

    // Update recent activity
    updateRecentActivity(`[${new Date().toLocaleTimeString()}] Device key provisioned`);
    // Refresh key status
    fetchKey();
})
    .catch(error => {
    document.getElementById('keyLogDevice').textContent = 'Error: ' + error.message;
})
    .finally(() => {
    hideSpinner('keyDeviceSpinner');
});
}

    function resetKey() {
    showSpinner('keyDeviceSpinner');
    document.getElementById('keyLogDevice').textContent = '';

    fetch(`${api}/device/reset`, { method: 'POST' })
    .then(handleResponse)
    .then(data => {
    document.getElementById('keyLogDevice').textContent = JSON.stringify(data, null, 2);

    // Update recent activity
    updateRecentActivity(`[${new Date().toLocaleTimeString()}] Device key reset`);
    // Refresh key status
    fetchKey();
})
    .catch(error => {
    document.getElementById('keyLogDevice').textContent = 'Error: ' + error.message;
})
    .finally(() => {
    hideSpinner('keyDeviceSpinner');
});
}

    function showDecryptModal(filename) {
    document.getElementById('decryptFileName').textContent = filename;
    document.getElementById('decryptModal').style.display = 'flex';
}

    function decryptSelectedFile() {
    const filename = document.getElementById('decryptFileName').textContent;
    const keyFileInput = document.getElementById('modalKeyFile');
    const keyFile = keyFileInput.files[0];
    const userPassword = document.getElementById('modalPassword').value;

    if (!userPassword) {
    alert('Please enter a password');
    return;
}

    if (!keyFile) {
    alert('Please select a key file');
    return;
}

    showSpinner('modalSpinner');

    // Get encrypted file from server
    fetch(`${api}/files/download/encrypted/${encodeURIComponent(filename)}`)
    .then(response => {
    if (!response.ok) throw new Error('File not found on server');
    return response.blob();
})
    .then(blob => {
    const encryptedFile = new File([blob], filename, { type: 'application/octet-stream' });

    const formData = new FormData();
    formData.append("encryptedFile", encryptedFile);
    formData.append("keyFile", keyFile);
    formData.append("userPassword", userPassword);

    return fetch(`${api}/files/decrypt`, { method: 'POST', body: formData });
})
    .then(response => {
    if (!response.ok) throw new Error('Decryption failed');
    return response.blob();
})
    .then(blob => {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'decrypted_' + filename;
    a.click();

    // Update UI
    document.getElementById('decryptLog').textContent = `✅ File "${filename}" decrypted successfully`;
    updateRecentActivity(`[${new Date().toLocaleTimeString()}] File "${filename}" decrypted`);
})
    .catch(error => {
    document.getElementById('decryptLog').textContent = 'Error: ' + error.message;
})
    .finally(() => {
    hideSpinner('modalSpinner');
    document.getElementById('decryptModal').style.display = 'none';
    // Clear form
    keyFileInput.value = '';
    document.getElementById('modalPassword').value = '';
});
}

    function updateRecentActivity(message) {
    const activityLog = document.getElementById('recentActivity');
    const now = new Date();
    const timestamp = `[${now.getHours()}:${now.getMinutes().toString().padStart(2, '0')}]`;
    activityLog.innerHTML = `${timestamp} ${message}<br>${activityLog.innerHTML}`;

    // Keep only the last 5 activities
    const activities = activityLog.innerHTML.split('<br>');
    if (activities.length > 5) {
    activityLog.innerHTML = activities.slice(0, 5).join('<br>');
}
}

    // Handle API responses consistently
    function handleResponse(response) {
    if (!response.ok) {
    return response.json().then(data => {
    throw new Error(data.error || 'Request failed');
});
}
    return response.json();
}

    // Initialize
    window.onload = function() {
    pingDevice();
    fetchKey();
    listFiles();
};
