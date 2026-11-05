from fastapi import FastAPI

app = FastAPI(title="Reusable Project Starter")

@app.get("/")
def root():
    return {"message": "Project starter is running"}

@app.get("/ready")
def ready():
    return {"status": "ok"}
