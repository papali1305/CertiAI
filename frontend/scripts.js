// DOM Elements
const themeToggle = document.getElementById('theme-toggle');
const tabButtons = document.querySelectorAll('.tab-button');
const tabContents = document.querySelectorAll('.tab-content');
const tabIndicator = document.querySelector('.tab-indicator');
const certificateForm = document.getElementById('certificate-form');
const verifyForm = document.getElementById('verify-form');
const methodCards = document.querySelectorAll('.method-card');
const methodContents = document.querySelectorAll('.method-content');
const qrDropZone = document.getElementById('qr-drop-zone');
const qrFileInput = document.getElementById('qr-file');
const browseQrBtn = document.getElementById('browse-qr');
const qrPreview = document.getElementById('qr-preview');
const verificationResult = document.getElementById('verification-result');
const certificateModal = document.getElementById('certificate-modal');
const closeModalBtn = document.getElementById('close-modal');
const loadingOverlay = document.getElementById('loading-overlay');
const loadingText = document.getElementById('loading-text');
const participantNameInput = document.getElementById('participant-name');
const nameSuggestions = document.getElementById('name-suggestions');
const filterButtons = document.querySelectorAll('.filter-btn');
const templatesGrid = document.querySelector('.templates-grid');
const applyTemplateBtn = document.getElementById('apply-template');
const primaryColorInput = document.getElementById('primary-color');
const secondaryColorInput = document.getElementById('secondary-color');
const fontSelector = document.getElementById('font-selector');
const borderOptions = document.querySelectorAll('.border-option');
const downloadButtons = document.querySelectorAll('.btn-download');
const shareButtons = document.querySelectorAll('.btn-share');

// Templates Data
const templates = [
    {
        id: 'classic',
        name: 'Classic',
        category: 'academic',
        thumbnail: 'assets/templates/classic.jpg',
        colors: ['#4361ee', '#3f37c9'],
        font: 'Inter',
        border: 'classic'
    },
    {
        id: 'modern',
        name: 'Modern',
        category: 'professional',
        thumbnail: 'assets/templates/modern.jpg',
        colors: ['#4cc9f0', '#4895ef'],
        font: 'Roboto',
        border: 'modern'
    },
    {
        id: 'elegant',
        name: 'Elegant',
        category: 'professional',
        thumbnail: 'assets/templates/elegant.jpg',
        colors: ['#7209b7', '#b5179e'],
        font: 'Playfair Display',
        border: 'elegant'
    },
    {
        id: 'creative',
        name: 'Creative',
        category: 'creative',
        thumbnail: 'assets/templates/creative.jpg',
        colors: ['#f72585', '#b5179e'],
        font: 'Montserrat',
        border: 'modern'
    },
    {
        id: 'minimal',
        name: 'Minimal',
        category: 'professional',
        thumbnail: 'assets/templates/minimal.jpg',
        colors: ['#212529', '#6c757d'],
        font: 'Inter',
        border: 'modern'
    }
];

// Current Template
let currentTemplate = templates[0];

// Initialize the app
function init() {
    // Set up theme toggle
    setupTheme();
    
    // Set up tabs
    setupTabs();
    
    // Set up verification methods
    setupVerificationMethods();
    
    // Set up QR code upload
    setupQrUpload();
    
    // Set up form validation
    setupFormValidation();
    
    // Load templates
    loadTemplates();
    
    // Set up template customization
    setupTemplateCustomization();
    
    // Set up modal
    setupModal();
}

// Theme Setup
function setupTheme() {
    // Check for saved theme preference or use system preference
    const savedTheme = localStorage.getItem('theme') || 
                      (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    document.documentElement.setAttribute('data-theme', savedTheme);
    
    themeToggle.addEventListener('click', () => {
        const currentTheme = document.documentElement.getAttribute('data-theme');
        const newTheme = currentTheme === 'light' ? 'dark' : 'light';
        document.documentElement.setAttribute('data-theme', newTheme);
        localStorage.setItem('theme', newTheme);
    });
}

// Tab Setup
function setupTabs() {
    tabButtons.forEach((button, index) => {
        button.addEventListener('click', () => {
            // Update active tab button
            tabButtons.forEach(btn => btn.classList.remove('active'));
            button.classList.add('active');
            
            // Move tab indicator
            const buttonWidth = button.offsetWidth;
            const buttonLeft = button.offsetLeft;
            tabIndicator.style.width = `${buttonWidth}px`;
            tabIndicator.style.transform = `translateX(${buttonLeft}px)`;
            
            // Show corresponding content
            const tabName = button.getAttribute('data-tab');
            tabContents.forEach(content => {
                content.classList.remove('active');
                if (content.id === `${tabName}-tab`) {
                    content.classList.add('active');
                }
            });
        });
    });
    
    // Initialize tab indicator position
    const activeTab = document.querySelector('.tab-button.active');
    if (activeTab) {
        const buttonWidth = activeTab.offsetWidth;
        const buttonLeft = activeTab.offsetLeft;
        tabIndicator.style.width = `${buttonWidth}px`;
        tabIndicator.style.transform = `translateX(${buttonLeft}px)`;
    }
}

// Verification Methods Setup
function setupVerificationMethods() {
    methodCards.forEach(card => {
        card.addEventListener('click', () => {
            const method = card.getAttribute('data-method');
            
            // Update active method card
            methodCards.forEach(c => c.classList.remove('active'));
            card.classList.add('active');
            
            // Show corresponding method content
            methodContents.forEach(content => {
                content.classList.remove('active');
                if (content.id === `verify-${method}`) {
                    content.classList.add('active');
                }
            });
        });
    });
}

// QR Upload Setup
function setupQrUpload() {
    // Handle drag and drop
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        qrDropZone.addEventListener(eventName, preventDefaults, false);
    });
    
    function preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }
    
    ['dragenter', 'dragover'].forEach(eventName => {
        qrDropZone.addEventListener(eventName, highlight, false);
    });
    
    ['dragleave', 'drop'].forEach(eventName => {
        qrDropZone.addEventListener(eventName, unhighlight, false);
    });
    
    function highlight() {
        qrDropZone.classList.add('highlight');
    }
    
    function unhighlight() {
        qrDropZone.classList.remove('highlight');
    }
    
    // Handle dropped files
    qrDropZone.addEventListener('drop', handleDrop, false);
    
    function handleDrop(e) {
        const dt = e.dataTransfer;
        const files = dt.files;
        handleFiles(files);
    }
    
    // Handle file input
    qrFileInput.addEventListener('change', () => {
        if (qrFileInput.files.length) {
            handleFiles(qrFileInput.files);
        }
    });
    
    // Browse button
    browseQrBtn.addEventListener('click', () => {
        qrFileInput.click();
    });
    
    function handleFiles(files) {
        const file = files[0];
        if (file.type.match('image.*')) {
            const reader = new FileReader();
            reader.onload = function(e) {
                qrPreview.innerHTML = '';
                const img = document.createElement('img');
                img.src = e.target.result;
                qrPreview.appendChild(img);
                
                // In a real app, you would decode the QR code here
                // For demo purposes, we'll simulate it
                simulateQrDecode(file.name);
            };
            reader.readAsDataURL(file);
        }
    }
    
    function simulateQrDecode(filename) {
        // Extract "certificate ID" from filename (for demo purposes)
        const certificateId = filename.split('.')[0];
        document.getElementById('certificate-id').value = certificateId;
    }
}

// Form Validation Setup
function setupFormValidation() {
    // Name validation with debounce
    participantNameInput.addEventListener('input', debounce(() => {
        const name = participantNameInput.value.trim();
        if (name.length > 2) {
            validateName(name);
        } else {
            nameSuggestions.style.display = 'none';
        }
    }, 500));
    
    // Form submission
    certificateForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        await generateCertificate();
    });
    
    // Verify form submission
    verifyForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        await verifyCertificate();
    });
}

// Debounce function
function debounce(func, wait) {
    let timeout;
    return function() {
        const context = this, args = arguments;
        clearTimeout(timeout);
        timeout = setTimeout(() => {
            func.apply(context, args);
        }, wait);
    };
}

// Name Validation
async function validateName(name) {
    try {
        const response = await fetch('http://localhost:5000/validate_name', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ name })
        });
        
        const data = await response.json();
        
        nameSuggestions.innerHTML = '';
        
        if (data.suggestions && data.suggestions.length > 0) {
            data.suggestions.forEach(suggestion => {
                const suggestionItem = document.createElement('div');
                suggestionItem.className = 'suggestion-item';
                suggestionItem.textContent = suggestion;
                suggestionItem.addEventListener('click', () => {
                    participantNameInput.value = suggestion;
                    nameSuggestions.style.display = 'none';
                });
                nameSuggestions.appendChild(suggestionItem);
            });
            nameSuggestions.style.display = 'block';
        } else {
            nameSuggestions.style.display = 'none';
        }
    } catch (error) {
        console.error('Error validating name:', error);
    }
}

// Generate Certificate
async function generateCertificate() {
    showLoading('Generating certificate...');
    
    const formData = {
        participantName: document.getElementById('participant-name').value,
        participantEmail: document.getElementById('participant-email').value,
        certificateTitle: document.getElementById('certificate-title').value,
        courseName: document.getElementById('course-name').value,
        completionDate: document.getElementById('completion-date').value,
        expiryDate: document.getElementById('expiry-date').value,
        issuerName: document.getElementById('issuer-name').value,
        issuerSignature: document.getElementById('issuer-signature').value,
        template: currentTemplate
    };
    
    try {
        // In a real app, you would send this to your backend
        // For demo purposes, we'll simulate the response
        const certificateData = await simulateCertificateGeneration(formData);
        
        // Display the certificate preview
        displayCertificatePreview(certificateData);
        
        // Show the modal
        certificateModal.classList.add('active');
    } catch (error) {
        console.error('Error generating certificate:', error);
        alert('Failed to generate certificate. Please try again.');
    } finally {
        hideLoading();
    }
}

// Simulate Certificate Generation
function simulateCertificateGeneration(formData) {
    return new Promise((resolve) => {
        setTimeout(() => {
            const certificateId = `cert-${Math.random().toString(36).substr(2, 9)}`;
            
            // Format date
            const dateObj = new Date(formData.completionDate);
            const formattedDate = dateObj.toLocaleDateString('en-US', { 
                year: 'numeric', month: 'long', day: 'numeric' 
            });
            
            // Simulate QR code generation
            const qrCodeUrl = `https://certiai.example.com/verify/${certificateId}`;
            
            resolve({
                certificateId,
                ...formData,
                formattedDate,
                qrCodeUrl,
                issueDate: new Date().toISOString()
            });
        }, 1500);
    });
}

// Display Certificate Preview
function displayCertificatePreview(data) {
    const preview = document.getElementById('certificate-preview');
    
    // Update preview content
    preview.innerHTML = `
        <div class="certificate" style="
            border-image: linear-gradient(135deg, ${currentTemplate.colors[0]}, ${currentTemplate.colors[1]}) 1;
            font-family: '${currentTemplate.font}', sans-serif;
        ">
            <div class="certificate-header">
                <h2>${data.certificateTitle}</h2>
                <p>This is to certify that</p>
            </div>
            <div class="participant-name">${data.participantName}</div>
            <div class="certificate-body">
                <p>has successfully completed the program</p>
                <p class="course-name">${data.courseName}</p>
                <p>on ${data.formattedDate}</p>
            </div>
            <div class="certificate-footer">
                <div class="issuer-signature">
                    <div class="signature-line"></div>
                    <p>${data.issuerSignature}</p>
                    <p>${data.issuerName}</p>
                </div>
                <div class="qr-code">
                    <img src="https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(data.qrCodeUrl)}" alt="Certificate QR Code">
                </div>
            </div>
        </div>
    `;
    
    // Store certificate data for download
    preview.dataset.certificateData = JSON.stringify(data);
}

// Verify Certificate
async function verifyCertificate() {
    const certificateId = document.getElementById('certificate-id').value;
    
    if (!certificateId) {
        showVerificationResult(false, 'Please enter a certificate ID or upload QR code');
        return;
    }
    
    showLoading('Verifying certificate...');
    
    try {
        // In a real app, you would verify with your backend
        // For demo purposes, we'll simulate verification
        const isValid = await simulateVerification(certificateId);
        
        if (isValid) {
            const certificateData = {
                participantName: 'John Doe',
                courseName: 'Advanced AI Programming',
                completionDate: '2023-05-15',
                issuerName: 'Dr. Sarah Smith'
            };
            showVerificationResult(true, 'Certificate is valid', certificateData);
        } else {
            showVerificationResult(false, 'Certificate not found or invalid');
        }
    } catch (error) {
        console.error('Error verifying certificate:', error);
        showVerificationResult(false, 'Error verifying certificate');
    } finally {
        hideLoading();
    }
}

// Simulate Verification
function simulateVerification(certificateId) {
    return new Promise((resolve) => {
        setTimeout(() => {
            // Simulate verification - in a real app, this would check your database
            resolve(certificateId.startsWith('cert-'));
        }, 1000);
    });
}

// Show Verification Result
function showVerificationResult(isValid, message, certificateData = null) {
    verificationResult.innerHTML = '';
    
    const resultDiv = document.createElement('div');
    resultDiv.className = isValid ? 'valid-result' : 'invalid-result';
    
    const header = document.createElement('div');
    header.className = 'result-header';
    
    const icon = document.createElement('div');
    icon.className = 'result-icon';
    icon.innerHTML = isValid ? '<i class="fas fa-check-circle"></i>' : '<i class="fas fa-times-circle"></i>';
    
    const title = document.createElement('div');
    title.className = 'result-title';
    title.textContent = isValid ? 'Valid Certificate' : 'Invalid Certificate';
    
    header.appendChild(icon);
    header.appendChild(title);
    
    const messageDiv = document.createElement('div');
    messageDiv.className = 'result-message';
    messageDiv.textContent = message;
    
    resultDiv.appendChild(header);
    resultDiv.appendChild(messageDiv);
    
    if (isValid && certificateData) {
        const details = document.createElement('div');
        details.className = 'result-details';
        
        const detail1 = createDetailRow('Name:', certificateData.participantName);
        const detail2 = createDetailRow('Course:', certificateData.courseName);
        const detail3 = createDetailRow('Date:', new Date(certificateData.completionDate).toLocaleDateString());
        const detail4 = createDetailRow('Issued by:', certificateData.issuerName);
        
        details.appendChild(detail1);
        details.appendChild(detail2);
        details.appendChild(detail3);
        details.appendChild(detail4);
        
        resultDiv.appendChild(details);
    }
    
    verificationResult.appendChild(resultDiv);
}

function createDetailRow(label, value) {
    const row = document.createElement('div');
    row.className = 'result-detail';
    
    const labelSpan = document.createElement('span');
    labelSpan.className = 'result-label';
    labelSpan.textContent = label;
    
    const valueSpan = document.createElement('span');
    valueSpan.className = 'result-value';
    valueSpan.textContent = value;
    
    row.appendChild(labelSpan);
    row.appendChild(valueSpan);
    
    return row;
}

// Load Templates
function loadTemplates(filter = 'all') {
    templatesGrid.innerHTML = '';
    
    const filteredTemplates = filter === 'all' 
        ? templates 
        : templates.filter(t => t.category === filter);
    
    filteredTemplates.forEach(template => {
        const templateItem = document.createElement('div');
        templateItem.className = 'template-item';
        templateItem.dataset.templateId = template.id;
        
        templateItem.innerHTML = `
            <img src="${template.thumbnail}" alt="${template.name} Template">
            <div class="template-overlay">
                <button class="template-select-btn">Select Template</button>
            </div>
        `;
        
        templateItem.addEventListener('click', () => {
            selectTemplate(template);
        });
        
        templatesGrid.appendChild(templateItem);
    });
}

// Select Template
function selectTemplate(template) {
    currentTemplate = template;
    
    // Update customization controls
    primaryColorInput.value = template.colors[0];
    secondaryColorInput.value = template.colors[1];
    fontSelector.value = template.font;
    
    // Update border option
    borderOptions.forEach(option => {
        option.classList.remove('active');
        if (option.dataset.border === template.border) {
            option.classList.add('active');
        }
    });
    
    // Scroll to customization section
    document.querySelector('.template-customization').scrollIntoView({
        behavior: 'smooth'
    });
}

// Setup Template Customization
function setupTemplateCustomization() {
    // Filter buttons
    filterButtons.forEach(button => {
        button.addEventListener('click', () => {
            filterButtons.forEach(btn => btn.classList.remove('active'));
            button.classList.add('active');
            loadTemplates(button.dataset.filter);
        });
    });
    
    // Apply template button
    applyTemplateBtn.addEventListener('click', () => {
        // Update current template with customization
        currentTemplate = {
            ...currentTemplate,
            colors: [primaryColorInput.value, secondaryColorInput.value],
            font: fontSelector.value,
            border: document.querySelector('.border-option.active').dataset.border
        };
        
        // Show confirmation
        alert('Template customization applied successfully!');
    });
    
    // Border options
    borderOptions.forEach(option => {
        option.addEventListener('click', () => {
            borderOptions.forEach(opt => opt.classList.remove('active'));
            option.classList.add('active');
        });
    });
    
    // Load initial templates
    loadTemplates();
}

// Setup Modal
function setupModal() {
    // Close modal button
    closeModalBtn.addEventListener('click', () => {
        certificateModal.classList.remove('active');
    });
    
    // Close when clicking outside
    certificateModal.addEventListener('click', (e) => {
        if (e.target === certificateModal) {
            certificateModal.classList.remove('active');
        }
    });
    
    // Download buttons
    downloadButtons.forEach(button => {
        button.addEventListener('click', () => {
            const format = button.dataset.format;
            downloadCertificate(format);
        });
    });
    
    // Share buttons
    shareButtons.forEach(button => {
        button.addEventListener('click', () => {
            const platform = button.dataset.platform;
            shareCertificate(platform);
        });
    });
}

// Download Certificate
function downloadCertificate(format) {
    const certificateData = JSON.parse(document.getElementById('certificate-preview').dataset.certificateData);
    
    showLoading(`Preparing ${format.toUpperCase()} download...`);
    
    setTimeout(() => {
        // In a real app, this would generate the actual file
        // For demo, we'll simulate it
        const link = document.createElement('a');
        link.href = `data:application/${format};charset=utf-8,${encodeURIComponent('Certificate content would be here')}`;
        link.download = `certificate_${certificateData.certificateId}.${format}`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        
        hideLoading();
    }, 1000);
}

// Share Certificate
function shareCertificate(platform) {
    const certificateData = JSON.parse(document.getElementById('certificate-preview').dataset.certificateData);
    
    let url, text;
    
    switch (platform) {
        case 'email':
            url = `mailto:?subject=My Certificate - ${certificateData.courseName}&body=Here's my certificate: https://certiai.example.com/verify/${certificateData.certificateId}`;
            break;
        case 'linkedin':
            url = `https://www.linkedin.com/shareArticle?mini=true&url=https://certiai.example.com/verify/${certificateData.certificateId}&title=${encodeURIComponent(`I earned a certificate in ${certificateData.courseName}`)}&summary=${encodeURIComponent(`Check out my certificate from ${certificateData.issuerName}`)}`;
            break;
        case 'twitter':
            text = `I just earned a certificate in ${certificateData.courseName} from ${certificateData.issuerName}! Check it out: https://certiai.example.com/verify/${certificateData.certificateId}`;
            url = `https://twitter.com/intent/tweet?text=${encodeURIComponent(text)}`;
            break;
        default:
            return;
    }
    
    window.open(url, '_blank');
}

// Show Loading Overlay
function showLoading(message) {
    loadingText.textContent = message;
    loadingOverlay.classList.add('active');
}

// Hide Loading Overlay
function hideLoading() {
    loadingOverlay.classList.remove('active');
}

// Initialize the app when DOM is loaded
document.addEventListener('DOMContentLoaded', init);