// Trivia Game Application with WebSocket Multiplayer
(function() {
    // State
    let ws = null;
    let currentRoom = null;
    let currentPlayer = null;
    let currentQuestionIndex = 0;
    let totalQuestions = 10;
    let answered = false;
    let currentCorrectAnswer = null;
    let timerInterval = null;
    let timeRemaining = 30;
    let players = [];

    // DOM Elements
    const screens = {
        home: document.getElementById('home-screen'),
        createRoom: document.getElementById('create-room-screen'),
        roomCreated: document.getElementById('room-created-screen'),
        joinRoom: document.getElementById('join-room-screen'),
        game: document.getElementById('game-screen'),
        gameOver: document.getElementById('game-over-screen')
    };

    const buttons = {
        createRoom: document.getElementById('create-room-btn'),
        joinRoom: document.getElementById('join-room-btn'),
        create: document.getElementById('create-btn'),
        createBack: document.getElementById('create-back-btn'),
        join: document.getElementById('join-btn'),
        joinBack: document.getElementById('join-back-btn'),
        startGame: document.getElementById('start-game-btn'),
        createdBack: document.getElementById('created-back-btn'),
        nextQuestion: document.getElementById('next-question-btn'),
        playAgain: document.getElementById('play-again-btn'),
        home: document.getElementById('home-btn'),
        leave: document.getElementById('leave-btn')
    };

    const elements = {
        ageGroup: document.getElementById('age-group'),
        topic: document.getElementById('topic'),
        roomCreatedTitle: document.getElementById('room-created-title'),
        roomCode: document.getElementById('room-code-text'),
        roomAgeGroup: document.getElementById('room-age-group'),
        roomTopic: document.getElementById('room-topic'),
        playerNameDisplay: document.getElementById('player-name-display'),
        playerNameCreate: document.getElementById('player-name-create'),
        playerNameJoin: document.getElementById('player-name-join'),
        roomCodeInput: document.getElementById('room-code-input'),
        questionNumber: document.getElementById('question-number'),
        timerDisplay: document.getElementById('timer-display'),
        timerFill: document.getElementById('timer-fill'),
        questionText: document.getElementById('question-text'),
        answersContainer: document.getElementById('answers-container'),
        progressFill: document.getElementById('progress-fill'),
        feedbackOverlay: document.getElementById('feedback-overlay'),
        feedbackIcon: document.getElementById('feedback-icon'),
        feedbackText: document.getElementById('feedback-text'),
        answerResults: document.getElementById('answer-results'),
        finalScoresList: document.getElementById('final-scores-list'),
        playersList: document.getElementById('players-list'),
        playerCount: document.getElementById('player-count'),
        playerScores: document.getElementById('player-scores'),
        headerRoomCode: document.getElementById('header-room-code-text'),
        headerPlayerName: document.getElementById('header-player-name-text'),
        headerPlayerScore: document.getElementById('header-player-score-text'),
        hostControls: document.getElementById('host-controls'),
        waitingMessage: document.getElementById('waiting-message'),
        connectionStatus: document.getElementById('connection-status'),
        statusText: document.getElementById('status-text')
    };

    // Screen Navigation
    function showScreen(screenName) {
        Object.values(screens).forEach(screen => {
            if (!screen.classList.contains('hidden')) {
                screen.classList.add('hidden');
            }
        });
        screens[screenName].classList.remove('hidden');
    }

    function goHome() {
        disconnectWebSocket();
        currentRoom = null;
        currentPlayer = null;
        currentQuestionIndex = 0;
        answered = false;
        players = [];
        elements.ageGroup.value = '';
        elements.topic.value = '';
        elements.playerNameCreate.value = '';
        elements.playerNameJoin.value = '';
        elements.roomCodeInput.value = '';
        showScreen('home');
    }

    // WebSocket Functions
    function connectWebSocket(onConnect) {
        const wsUrl = `ws://${window.location.host}/ws/game`;
        ws = new WebSocket(wsUrl);

        ws.onopen = () => {
            console.log('WebSocket connected');
            showConnectionStatus('Connected', 'success');
            if (onConnect) onConnect();
        };

        ws.onmessage = (event) => {
            const message = JSON.parse(event.data);
            handleWebSocketMessage(message);
        };

        ws.onclose = () => {
            console.log('WebSocket disconnected');
            showConnectionStatus('Disconnected', 'error');
        };

        ws.onerror = (error) => {
            console.error('WebSocket error:', error);
            showConnectionStatus('Connection error', 'error');
        };
    }

    function disconnectWebSocket() {
        if (ws) {
            ws.close();
            ws = null;
        }
        stopTimer();
    }

    function sendWebSocketMessage(message) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(message));
        }
    }

    function handleWebSocketMessage(message) {
        console.log('WS Message:', message.type, message);

        switch (message.type) {
            case 'PLAYER_JOINED':
                handlePlayerJoined(message);
                break;
            case 'GAME_STARTED':
                handleGameStarted(message);
                break;
            case 'NEW_QUESTION':
                handleNewQuestion(message);
                break;
            case 'TIMER_UPDATE':
                handleTimerUpdate(message);
                break;
            case 'PLAYER_ANSWERED':
                handlePlayerAnswered(message);
                break;
            case 'SHOW_ANSWER':
                handleShowAnswer(message);
                break;
            case 'GAME_OVER':
                handleGameOver(message);
                break;
            case 'PLAYER_LEFT':
                handlePlayerLeft(message);
                break;
            case 'ERROR':
                handleError(message);
                break;
        }
    }

    // Message Handlers
    function handlePlayerJoined(message) {
        const data = message.data;
        if (data.playerId && !currentPlayer) {
            // This is our join confirmation
            currentPlayer = {
                id: data.playerId,
                name: data.playerName,
                isHost: data.isHost
            };
            updateSessionHeader();
        }
        
        players = data.players || [];
        updatePlayersList();
        updatePlayerScores();

        if (currentPlayer && !currentPlayer.isHost) {
            elements.roomAgeGroup.textContent = data.ageGroup;
            elements.roomTopic.textContent = data.topic;
            elements.roomCreatedTitle.innerText = 'Joined the Room!'
        }

        if (currentPlayer && currentPlayer.isHost) {
            elements.hostControls.classList.remove('hidden');
            elements.waitingMessage.classList.add('hidden');
            elements.roomCreatedTitle.innerText = 'Room Created!'
        }
    }

    function handleGameStarted(message) {
        const data = message.data;
        totalQuestions = data.totalQuestions;
        players = data.players || [];
        showScreen('game');
        updateSessionHeader();
        updatePlayerScores();
    }

    function handleNewQuestion(message) {
        const data = message.data;
        currentQuestionIndex = data.questionIndex - 1;
        answered = false;
        
        // Reset UI
        elements.questionText.textContent = data.question;
        elements.questionText.classList.add('question-transition');
        elements.answersContainer.innerHTML = '';
        elements.feedbackOverlay.classList.add('hidden');
        //elements.nextQuestion.classList.add('hidden');
        elements.answerResults.innerHTML = '';
        
        // Store correct answer (sent separately from shuffled options)
        currentCorrectAnswer = data.correctAnswer;
        
        // Create answer buttons with the shuffled options
        const colors = ['red', 'blue', 'yellow', 'green'];
        data.options.forEach((option, index) => {
            const btn = document.createElement('button');
            btn.className = `answer-btn ${colors[index % colors.length]}`;
            btn.textContent = option;
            btn.dataset.answer = option;
            btn.addEventListener('click', () => submitAnswer(option));
            elements.answersContainer.appendChild(btn);
        });
        
        // Start timer
        timeRemaining = data.timeLimit || 30;
        startTimer();

        // If we are not already on the game screen (e.g. joining mid-session), show it
        showScreen('game');

        // Update progress
        updateProgress();
        updatePlayerScores();

        // Keep header info in sync
        updateSessionHeader();

        setTimeout(() => {
            elements.questionText.classList.remove('question-transition');
        }, 500);
    }

    function handleTimerUpdate(message) {
        const data = message.data;
        timeRemaining = data.remaining || 0;
        elements.timerDisplay.textContent = `⏱️ ${timeRemaining}`;
        
        const progress = (timeRemaining / 30) * 100;
        elements.timerFill.style.width = `${progress}%`;
        updatePlayerScores();
    }

    function handlePlayerAnswered(message) {
        const data = message.data;
        players = data.players || [];
        
        // If this is our answer result, show feedback
        if (message.playerId === currentPlayer?.id && data.correctAnswer !== undefined) {
            // show points awarded when present
            const points = data.pointsAwarded || 0;
            showAnswerFeedback(data.isCorrect, data.correctAnswer, data.score, points);
        }

        // If server did not include a full players list (private message), update our local player entry
        if ((!data.players || data.players.length === 0) && message.playerId === currentPlayer?.id) {
            // update current player's score from private payload
            if (typeof data.score === 'number') {
                // update header score
                elements.headerPlayerScore.innerHTML = data.score;
                // update players array if we have this player locally
                const existing = players.find(p => p.id === currentPlayer.id);
                if (existing) existing.score = data.score;
            }
        }

        // Update scores UI
        updatePlayerScores();

        // Header may include player name — keep it updated
        updateSessionHeader();
    }

    function handleShowAnswer(message) {
        const data = message.data;
        players = data.players || [];
        
        // Highlight correct answer
        const answerBtns = elements.answersContainer.querySelectorAll('.answer-btn');
        answerBtns.forEach(btn => {
            btn.disabled = true;
            if (btn.dataset.answer === data.correctAnswer) {
                btn.classList.add('correct');
            } else if (btn.dataset.answer === currentPlayer?.lastAnswer) {
                btn.classList.add('wrong');
            }
        });
        
        // Show all player scores when time is up
        updatePlayerScores();

        // Show results breakdown
        showResults(data.players, data.correctAnswer);
        
        // Show next button for host
        if (currentPlayer?.isHost) {
            buttons.nextQuestion.classList.remove('hidden');
        }
    }

    function handleGameOver(message) {
        const data = message.data;
        stopTimer();
        
        // Sort and display final scores
        const sortedScores = (data.finalScores || []).sort((a, b) => b.score - a.score);
        elements.finalScoresList.innerHTML = '';
        
        sortedScores.forEach((player, index) => {
            const item = document.createElement('div');
            item.className = `final-score-item rank-${index + 1}`;
            item.innerHTML = `
                <span class="final-score-rank">${index + 1}</span>
                <span class="final-score-name">${player.name}</span>
                <span class="final-score-value">${player.score}</span>
            `;
            elements.finalScoresList.appendChild(item);
        });
        
        showScreen('gameOver');
    }

    function handlePlayerLeft(message) {
        const data = message.data;
        players = data.players || [];
        updatePlayersList();
        updatePlayerScores();
    }

    function handleError(message) {
        alert('Error: ' + message.data);
    }

    // UI Functions
    function updatePlayersList() {
        if (!elements.playersList) return;
        
        elements.playersList.innerHTML = '';
        players.forEach(player => {
            const li = document.createElement('li');
            li.className = player.isHost ? 'host' : '';
            if (player.id === currentPlayer?.id) {
                li.classList.add('you');
                li.textContent = `${player.name} (You)`;
            } else {
                li.textContent = player.name;
            }
            elements.playersList.appendChild(li);
        });
        
        elements.playerCount.textContent = players.length;
    }

    function updatePlayerScores() {

        players.forEach(player => {

            if (player.id === currentPlayer?.id) {

                elements.headerPlayerScore.innerHTML = player.score;
            }

        });
    }

    // Update the compact session header shown during the game
    function updateSessionHeader() {
        if (elements.headerRoomCode) {
            const roomCode = (currentRoom && currentRoom.roomCode) || elements.roomCode?.textContent || '';
            elements.headerRoomCode.textContent = roomCode;
        }
        if (elements.headerPlayerName) {
            const playerName = (currentPlayer && currentPlayer.name) || elements.playerNameDisplay?.textContent || elements.playerNameJoin?.value || elements.playerNameCreate?.value || '';
            elements.headerPlayerName.textContent = playerName;
        }
    }

    function updateProgress() {
        const progress = ((currentQuestionIndex + 1) / totalQuestions) * 100;
        elements.progressFill.style.width = `${progress}%`;
        elements.questionNumber.textContent = `Question ${currentQuestionIndex + 1}/${totalQuestions}`;
    }

    function startTimer() {
        stopTimer();
        timeRemaining = 30;
        elements.timerDisplay.textContent = `⏱️ ${timeRemaining}`;
        elements.timerFill.style.width = '100%';
    }

    function stopTimer() {
        if (timerInterval) {
            clearInterval(timerInterval);
            timerInterval = null;
        }
    }

    function submitAnswer(answer) {
        if (answered) return;
        answered = true;
        
        // Disable all buttons
        const answerBtns = elements.answersContainer.querySelectorAll('.answer-btn');
        answerBtns.forEach(btn => btn.disabled = true);
        
        // store locally so UI can reference our last answer immediately
        if (currentPlayer) currentPlayer.lastAnswer = answer;

        sendWebSocketMessage({
            type: 'SUBMIT_ANSWER',
            data: answer
        });
    }

    function showAnswerFeedback(isCorrect, correctAnswer, score, pointsAwarded) {
        elements.feedbackIcon.className = `feedback-icon ${isCorrect ? 'correct' : 'wrong'}`;
        if (isCorrect) {
            elements.feedbackText.textContent = `Correct! +${pointsAwarded} points`;
        } else {
            elements.feedbackText.textContent = `Wrong! The answer was: ${correctAnswer}`;
        }
    }

    function showResults(players, correctAnswer) {
        elements.answerResults.innerHTML = '';
        
        const sorted = [...players].sort((a, b) => b.score - a.score);
        sorted.forEach(player => {
            const isCorrect = player.lastAnswer === correctAnswer;
            const item = document.createElement('div');
            item.className = `answer-result-item ${isCorrect ? 'correct' : 'wrong'}`;
            item.innerHTML = `
                <span>${player.name}</span>
                <span>${isCorrect ? '✓' : '✗'} ${player.score}</span>
            `;
            elements.answerResults.appendChild(item);
        });
        
        elements.feedbackOverlay.classList.remove('hidden');
    }

    function showConnectionStatus(text, type) {
        elements.statusText.textContent = text;
        elements.connectionStatus.className = `connection-status ${type}`;
        elements.connectionStatus.classList.remove('hidden');
        
        setTimeout(() => {
            elements.connectionStatus.classList.add('hidden');
        }, 3000);
    }

    // API Calls (REST)
    async function createRoom(ageGroup, topic) {
        try {
            const response = await fetch('/api/rooms', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ageGroup, topic })
            });
            
            if (!response.ok) throw new Error('Failed to create room');
            return await response.json();
        } catch (error) {
            console.error('Error creating room:', error);
            alert('Failed to create room. Please try again.');
            return null;
        }
    }

    // Event Listeners
    buttons.createRoom.addEventListener('click', () => showScreen('createRoom'));
    buttons.joinRoom.addEventListener('click', () => showScreen('joinRoom'));
    buttons.createBack.addEventListener('click', goHome);
    buttons.joinBack.addEventListener('click', goHome);
    buttons.createdBack.addEventListener('click', goHome);

    buttons.create.addEventListener('click', async () => {
        const playerName = elements.playerNameCreate.value.trim();
        const ageGroup = elements.ageGroup.value.trim();
        const topic = elements.topic.value.trim();
        
        if (!playerName || !ageGroup || !topic) {
            alert('Please fill in all fields');
            return;
        }

        const room = await createRoom(ageGroup, topic);
        if (room) {
            currentRoom = room;
            elements.roomCode.textContent = room.roomCode;
            elements.roomAgeGroup.textContent = room.ageGroup;
            elements.roomTopic.textContent = room.topic;
            elements.playerNameDisplay.textContent = playerName;
            
            showScreen('roomCreated');
            
            // Connect WebSocket and join
            connectWebSocket(() => {
                sendWebSocketMessage({
                    type: 'JOIN_ROOM',
                    roomCode: room.roomCode,
                    playerName: playerName
                });
            });
        }
    });

    buttons.join.addEventListener('click', () => {
        const playerName = elements.playerNameJoin.value.trim();
        const roomCode = elements.roomCodeInput.value.trim();
        
        if (!playerName || !roomCode || roomCode.length !== 6) {
            alert('Please enter your name and a valid 6-digit room code');
            return;
        }

        currentRoom = { roomCode };
        
        showScreen('roomCreated');
        elements.roomCode.textContent = roomCode;
        elements.roomAgeGroup.textContent = '...';
        elements.roomTopic.textContent = '...';
        elements.playerNameDisplay.textContent = playerName;
        elements.hostControls.classList.add('hidden');
        elements.waitingMessage.classList.remove('hidden');
        
        // Connect WebSocket and join
        connectWebSocket(() => {
            sendWebSocketMessage({
                type: 'JOIN_ROOM',
                roomCode: roomCode,
                playerName: playerName
            });
        });
    });

    buttons.startGame.addEventListener('click', () => {
        if (!currentPlayer?.isHost) return;
        
        sendWebSocketMessage({
            type: 'GAME_STARTED'
        });
    });

    buttons.nextQuestion.addEventListener('click', () => {
        if (!currentPlayer?.isHost) return;
        
        sendWebSocketMessage({
            type: 'NEXT_QUESTION'
        });
    });

    buttons.playAgain.addEventListener('click', () => {
        if (currentPlayer?.isHost) {
            elements.feedbackOverlay.classList.add('hidden');
            buttons.nextQuestion.classList.remove('hidden');
            showScreen('roomCreated');
        } else {
            goHome();
        }
    });

    buttons.home.addEventListener('click', goHome);
    // Leave button: confirm and send leave
    if (buttons.leave) {
        buttons.leave.addEventListener('click', () => {
            if (!confirm('Are you sure you want to leave the room?')) return;
            // Inform server and go home
            sendWebSocketMessage({ type: 'PLAYER_LEAVE' });
            goHome();
        });
    }

    // Initialize
    console.log('Trivia Game initialized with WebSocket Multiplayer');
})();
