import logging
from logging.handlers import RotatingFileHandler
import os
import re
import threading
import unicodedata
from concurrent.futures import ThreadPoolExecutor
from functools import lru_cache
from typing import List, Dict, Tuple

import numpy as np
import pandas as pd
from flask import Flask, jsonify, request
from flask_cors import CORS
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
import spacy
from difflib import get_close_matches

# Initialize Flask app with enhanced configuration
app = Flask(__name__)
CORS(app, resources={r"/validate_name": {"origins": "*"}})

# Configure rate limiting
limiter = Limiter(
    app=app,
    key_func=get_remote_address,
    default_limits=["200 per day", "50 per hour"]
)

# Enhanced logging configuration
def setup_logging():
    log_dir = "logs"
    if not os.path.exists(log_dir):
        os.makedirs(log_dir)
    
    file_handler = RotatingFileHandler(
        f'{log_dir}/name_validator.log',
        maxBytes=1024 * 1024,
        backupCount=5
    )
    file_handler.setFormatter(logging.Formatter(
        '%(asctime)s %(levelname)s: %(message)s [in %(pathname)s:%(lineno)d]'
    ))
    file_handler.setLevel(logging.INFO)
    
    app.logger.addHandler(file_handler)
    app.logger.setLevel(logging.INFO)
    app.logger.info('Name validation service starting...')

setup_logging()

# Load NLP models
try:
    nlp = spacy.load("en_core_web_sm")
except OSError:
    app.logger.error("Spacy model not found. Downloading...")
    from spacy.cli import download
    download("en_core_web_sm")
    nlp = spacy.load("en_core_web_sm")

# Custom spell checker implementation
class NameSpellChecker:
    def __init__(self):
        self.word_frequency = {}
        self.load_word_list()
    
    def load_word_list(self):
        try:
            with open('./data/names_corpus.txt', 'r', encoding='utf-8') as f:
                for line in f:
                    word = line.strip().lower()
                    if word:
                        self.word_frequency[word] = self.word_frequency.get(word, 0) + 1
        except FileNotFoundError:
            app.logger.warning("Custom names corpus not found. Using default names.")
            default_names = ['john', 'mary', 'robert', 'jennifer', 'michael', 'linda']
            for name in default_names:
                self.word_frequency[name] = 1
    
    def candidates(self, word):
        word = word.lower()
        if word in self.word_frequency:
            return {word}
        
        suggestions = set()
        # Add simple variations
        for known_word in self.word_frequency:
            if self.edit_distance(word, known_word) <= 2:
                suggestions.add(known_word)
        
        return suggestions or {word}
    
    @staticmethod
    def edit_distance(s1, s2):
        if len(s1) < len(s2):
            return NameSpellChecker.edit_distance(s2, s1)
        
        if len(s2) == 0:
            return len(s1)
        
        previous_row = range(len(s2) + 1)
        for i, c1 in enumerate(s1):
            current_row = [i + 1]
            for j, c2 in enumerate(s2):
                insertions = previous_row[j + 1] + 1
                deletions = current_row[j] + 1
                substitutions = previous_row[j] + (c1 != c2)
                current_row.append(min(insertions, deletions, substitutions))
            previous_row = current_row
        
        return previous_row[-1]

# Initialize our custom spell checker
spell = NameSpellChecker()

# Load common names dataset
@lru_cache(maxsize=1)
def load_common_names() -> pd.DataFrame:
    try:
        common_names = pd.read_csv('./data/common_names.csv')
        return common_names
    except FileNotFoundError:
        app.logger.warning("Common names dataset not found. Using fallback data.")
        return pd.DataFrame({
            'name': ['John', 'Mary', 'Robert', 'Jennifer', 'Michael', 'Linda'],
            'gender': ['M', 'F', 'M', 'F', 'M', 'F'],
            'popularity': [1, 1, 2, 2, 3, 3]
        })

# Initialize TF-IDF vectorizer for name similarity
@lru_cache(maxsize=1)
def get_vectorizer():
    common_names = load_common_names()
    vectorizer = TfidfVectorizer(analyzer='char', ngram_range=(1, 3))
    vectorizer.fit(common_names['name'].tolist())
    return vectorizer

# Text preprocessing utilities
def normalize_text(text: str) -> str:
    """Normalize text by removing accents, special chars, and standardizing case"""
    text = unicodedata.normalize('NFKD', text).encode('ascii', 'ignore').decode('ascii')
    text = re.sub(r'[^a-zA-Z\s]', '', text)
    return text.strip().lower()

def is_valid_name(name: str) -> bool:
    """Check if a name follows basic naming conventions"""
    if len(name) < 2:
        return False
    if re.search(r'\d', name):
        return False
    return True

# Enhanced suggestion algorithms
def get_spell_suggestions(name_part: str) -> List[str]:
    """Get spelling suggestions for a name part"""
    candidates = spell.candidates(name_part)
    if not candidates:
        return []
    
    # Sort candidates by frequency (simple implementation)
    sorted_candidates = sorted(candidates, key=lambda x: spell.word_frequency.get(x, 0), reverse=True)
    return sorted_candidates[:3]

def get_semantic_suggestions(name: str, vectorizer) -> List[str]:
    """Get suggestions based on semantic similarity to common names"""
    common_names = load_common_names()
    if len(common_names) == 0:
        return []
    
    name_vec = vectorizer.transform([name])
    common_vecs = vectorizer.transform(common_names['name'])
    
    similarities = cosine_similarity(name_vec, common_vecs).flatten()
    top_indices = np.argsort(similarities)[-3:][::-1]
    return common_names.iloc[top_indices]['name'].tolist()

def analyze_name_structure(name: str) -> Dict:
    """Analyze name structure using NLP"""
    doc = nlp(name)
    analysis = {
        'likely_first': None,
        'likely_last': None,
        'tokens': [token.text for token in doc],
        'is_capitalized': name == name.title()
    }
    
    if len(doc) >= 2:
        analysis['likely_first'] = doc[0].text
        analysis['likely_last'] = ' '.join([t.text for t in doc[1:]])
    
    return analysis

# Main suggestion function with enhanced logic
def get_enhanced_suggestions(name: str) -> Tuple[List[str], Dict]:
    """Get enhanced name suggestions with detailed analysis"""
    if not is_valid_name(name):
        return [], {'error': 'Invalid name format'}
    
    normalized = normalize_text(name)
    parts = normalized.split()
    suggestions = []
    analysis = analyze_name_structure(name)
    vectorizer = get_vectorizer()
    
    # Multi-threaded suggestion generation
    with ThreadPoolExecutor() as executor:
        # Get spelling suggestions for each part
        spell_futures = [executor.submit(get_spell_suggestions, part) for part in parts]
        spell_results = [f.result() for f in spell_futures]
        
        # Get semantic suggestions for full name
        semantic_future = executor.submit(get_semantic_suggestions, normalized, vectorizer)
        semantic_results = semantic_future.result()
    
    # Combine spelling suggestions
    if any(spell_results):
        corrected_parts = []
        for i, part in enumerate(parts):
            if spell_results[i]:
                corrected_parts.append(spell_results[i][0])
            else:
                corrected_parts.append(part)
        
        corrected_name = ' '.join(corrected_parts)
        if corrected_name != normalized:
            suggestions.append(corrected_name.title())
    
    # Add semantic suggestions
    suggestions.extend([s.title() for s in semantic_results if s.title() not in suggestions])
    
    # Add original if no suggestions (might be correct)
    if not suggestions and len(parts) > 1:
        suggestions.append(name.title())
    
    # Limit and return
    return suggestions[:5], analysis

# API Endpoints
@app.route('/validate_name', methods=['POST'])
@limiter.limit("10 per minute")
def validate_name():
    """Enhanced name validation endpoint"""
    try:
        data = request.get_json()
        if not data:
            app.logger.warning("Empty request received")
            return jsonify({'error': 'No data provided'}), 400
        
        name = data.get('name', '').strip()
        if not name:
            app.logger.warning("Empty name parameter")
            return jsonify({'error': 'Name parameter is required'}), 400
        
        app.logger.info(f"Validating name: {name}")
        suggestions, analysis = get_enhanced_suggestions(name)
        
        response = {
            'original': name,
            'suggestions': suggestions,
            'analysis': analysis,
            'validation': {
                'is_valid': bool(suggestions) or is_valid_name(name),
                'normalized': normalize_text(name)
            },
            'metadata': {
                'service_version': '2.1.0',
                'model': 'spacy-en_core_web-sm'
            }
        }
        
        return jsonify(response)
    
    except Exception as e:
        app.logger.error(f"Error processing request: {str(e)}", exc_info=True)
        return jsonify({
            'error': 'Internal server error',
            'details': str(e)
        }), 500

# Health check endpoint
@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        'status': 'healthy',
        'services': {
            'spellchecker': 'active',
            'nlp': 'active',
            'similarity': 'active'
        }
    })

# Main execution
def run_server():
    app.run(
        host='0.0.0.0',
        port=5000,
        threaded=True,
        debug=False
    )

if __name__ == '__main__':
    # Create data directory if it doesn't exist
    if not os.path.exists('data'):
        os.makedirs('data')
        app.logger.info("Created data directory")
    
    # Run the server in a separate thread
    server_thread = threading.Thread(
        target=run_server,
        daemon=True,
        name='NameValidationServer'
    )
    server_thread.start()
    
    app.logger.info("Name validation service is running on port 5000")
    
    try:
        while True:
            server_thread.join(1)
    except KeyboardInterrupt:
        app.logger.info("Shutting down name validation service")