document.addEventListener('DOMContentLoaded', function() {
    // DOM Elements
    const lobbyScreen = document.getElementById('lobby-screen');
    const questionScreen = document.getElementById('question-screen');
    const resultsScreen = document.getElementById('results-screen');
    const finalResultsScreen = document.getElementById('final-results-screen');

    const roomCodeDisplay = document.getElementById('room-code-display');
    const startGameButton = document.getElementById('start-game-button');
    const nextQuestionBtn = document.getElementById('next-question-btn');
    const playAgainBtn = document.getElementById('play-again-btn');

    const questionText = document.getElementById('question-text');
    const timerProgress = document.getElementById('timer-progress');
    const timerText = document.getElementById('timer-text');
    const correctAnswerElement = document.getElementById('correct-answer');
    const scoreElement = document.getElementById('score');
    const finalScoreElement = document.getElementById('final-score');
    const finalPlayerScoreElement = document.getElementById('final-player-score');

    // Game state
    let questions = [];
    let currentQuestionIndex = 0;
    let score = 0;
    let timer;
    let timeLeft;
    let ageGroup, topic;

    // Initialize
    initGame();

    // Event Listeners
    startGameButton.addEventListener('click', startGame);
    nextQuestionBtn.addEventListener('click', nextQuestion);
    playAgainBtn.addEventListener('click', () => {
        window.location.href = '/';
    });

    // Add event listeners to answer options
    document.querySelectorAll('.answer-option').forEach(option => {
        option.addEventListener('click', function() {
            selectAnswer(this.dataset.index);
        });
    });

    // Functions
    function initGame() {
        // Get room info from localStorage
        const roomNumber = localStorage.getItem('roomNumber') || '123456';
        ageGroup = localStorage.getItem('ageGroup') || 'mixed';
        topic = localStorage.getItem('topic') || 'general';

        roomCodeDisplay.textContent = roomNumber;

        // Load questions from server with parameters
        fetch(`/api/trivia/questions?ageGroup=${encodeURIComponent(ageGroup)}&topic=${encodeURIComponent(topic)}`)
            .then(response => response.json())
            .then(data => {
                questions = data;
                console.log('Loaded questions:', questions);
            })
            .catch(error => {
                console.error('Error loading questions:', error);
                // Fallback to sample data
                questions = [
                    {
                        "question": "What is the capital of France?",
                        "options": ["London", "Berlin", "Paris", "Madrid"],
                        "correctAnswerIndex": 2
                    },
                    {
                        "question": "Which planet is known as the Red Planet?",
                        "options": ["Venus", "Mars", "Jupiter", "Saturn"],
                        "correctAnswerIndex": 1
                    },
                    {
                        "question": "What is the largest mammal in the world?",
                        "options": ["Elephant", "Blue Whale", "Giraffe", "Hippopotamus"],
                        "correctAnswerIndex": 1
                    },
                    {
                        "question": "Which element has the chemical symbol 'O'?",
                        "options": ["Gold", "Oxygen", "Osmium", "Oganesson"],
                        "correctAnswerIndex": 1
                    },
                    {
                        "question": "Who painted the Mona Lisa?",
                        "options": ["Vincent van Gogh", "Pablo Picasso", "Leonardo da Vinci", "Michelangelo"],
                        "correctAnswerIndex": 2
                    }
                ];
            });
    }

    function startGame() {
        lobbyScreen.classList.add('hidden');
        showQuestion();
    }

    function showQuestion() {
        if (currentQuestionIndex >= questions.length) {
            showFinalResults();
            return;
        }

        const question = questions[currentQuestionIndex];
        questionText.textContent = question.question;

        // Update answer options
        question.options.forEach((option, index) => {
            document.getElementById(`answer-${index}`).textContent = option;
        });

        // Reset answer options
        document.querySelectorAll('.answer-option').forEach(option => {
            option.classList.remove('selected', 'correct', 'incorrect');
        });

        // Show question screen
        questionScreen.classList.remove('hidden');
        resultsScreen.classList.add('hidden');

        // Start timer
        startTimer(30);
    }

    function startTimer(seconds) {
        timeLeft = seconds;
        updateTimerDisplay();

        timer = setInterval(() => {
            timeLeft--;
            updateTimerDisplay();

            if (timeLeft <= 0) {
                clearInterval(timer);
                timeUp();
            }
        }, 1000);
    }

    function updateTimerDisplay() {
        timerText.textContent = timeLeft;
        const progress = (timeLeft / 30) * 100;
        timerProgress.style.setProperty('--progress', `${100 - progress}%`);
    }

    function selectAnswer(selectedIndex) {
        // Stop timer
        clearInterval(timer);

        const question = questions[currentQuestionIndex];
        const correctIndex = question.correctAnswerIndex;

        // Highlight selected answer
        document.querySelectorAll('.answer-option').forEach((option, index) => {
            option.classList.remove('selected');
            if (index == selectedIndex) {
                option.classList.add('selected');
            }
        });

        // Check if answer is correct
        if (selectedIndex == correctIndex) {
            // Correct answer
            document.querySelector(`.answer-option[data-index="${selectedIndex}"]`).classList.add('correct');
            score += 100; // Add points
        } else {
            // Incorrect answer
            document.querySelector(`.answer-option[data-index="${selectedIndex}"]`).classList.add('incorrect');
            document.querySelector(`.answer-option[data-index="${correctIndex}"]`).classList.add('correct');
        }

        // Show results after a delay
        setTimeout(showResults, 1500);
    }

    function timeUp() {
        const question = questions[currentQuestionIndex];
        const correctIndex = question.correctAnswerIndex;

        // Highlight correct answer
        document.querySelector(`.answer-option[data-index="${correctIndex}"]`).classList.add('correct');

        // Show results after a delay
        setTimeout(showResults, 1500);
    }

    function showResults() {
        questionScreen.classList.add('hidden');
        resultsScreen.classList.remove('hidden');

        const question = questions[currentQuestionIndex];
        correctAnswerElement.textContent = question.options[question.correctAnswerIndex];
        scoreElement.textContent = score;
        finalScoreElement.textContent = score;
        finalPlayerScoreElement.textContent = score;
    }

    function nextQuestion() {
        currentQuestionIndex++;
        showQuestion();
    }

    function showFinalResults() {
        questionScreen.classList.add('hidden');
        resultsScreen.classList.add('hidden');
        finalResultsScreen.classList.remove('hidden');
    }
});