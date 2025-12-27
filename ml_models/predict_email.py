#!/usr/bin/env python3
import sys
import time

# Very small placeholder predictor: returns a message based on subject keywords
# Usage: python predict_email.py "Subject text"

if len(sys.argv) < 2:
    print("")
    sys.exit(0)

subject = sys.argv[1].lower()
# Simulate some processing delay
time.sleep(0.5)

if 'meeting' in subject or 'schedule' in subject:
    print("Hi,\n\nI'd like to schedule a meeting to discuss this further. Please let me know your availability.\n\nBest regards,")
elif 'greeting' in subject or 'hello' in subject:
    print("Hello,\n\nI hope you're doing well. Just wanted to reach out and say hi.\n\nRegards,")
else:
    print("Hi,\n\nThanks for reaching out. I'll get back to you soon with more details.\n\nThanks,")
