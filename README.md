# Smart Research Portal

A high-performance Java web application designed for real-time web research, factual data analysis, and automated summary generation.

## Requirements

- Java 17 or higher
- Maven 3.6+

## Getting Started

1. **Set your API Key**:
   Set your Google Gemini API key as an environment variable:
   - On Windows (CMD / PowerShell):
     ```cmd
     set GEMINI_API_KEY=your_api_key_here
     ```
     or in PowerShell:
     ```powershell
     $env:GEMINI_API_KEY="your_api_key_here"
     ```
   - On Linux / macOS:
     ```bash
     export GEMINI_API_KEY="your_api_key_here"
     ```

2. **Build the Application**:
   ```bash
   mvn package
   ```

3. **Run the Server**:
   ```bash
   java -jar target/smart-researcher.jar
   ```

4. **Access the Portal**:
   Open your browser and navigate to:
   [http://localhost:8080](http://localhost:8080)

## Architecture & Design

- **Backend**: Built using pure Java 17 and JDK `HttpServer` with modular REST API handlers (`/api/chat`).
- **Frontend**: Responsive Web UI built with modern HTML5, CSS3 glassmorphism design system, and Vanilla JavaScript.
- **Data Serialization**: Powered by Google Gson for lightweight JSON parsing.
