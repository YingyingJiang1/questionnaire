		public void run()
		{
			try
			{
				boolean animateFromBottom = true;
				GraphicsEnvironment ge = GraphicsEnvironment
						.getLocalGraphicsEnvironment();
				Rectangle screenRect = ge.getMaximumWindowBounds();

				int screenHeight = (int) screenRect.height;

				int startYPosition;
				int stopYPosition;

				if ( screenRect.y > 0 )
				{
				  animateFromBottom = false; // Animate from top!
				}

				maxToasterInSceen = screenHeight / toasterHeight;


				int posx = (int) screenRect.width - toasterWidth - 1;

				toaster.setLocation(posx, screenHeight);
				toaster.setVisible(true);
				if ( useAlwaysOnTop )
				{
				  toaster.setAlwaysOnTop(true);
				}

				if ( animateFromBottom )
				{
					startYPosition = screenHeight;
					stopYPosition = startYPosition - toasterHeight - 1;
					if ( currentNumberOfToaster > 0 )
					{
						stopYPosition = stopYPosition - ( maxToaster % maxToasterInSceen * toasterHeight );
					}
					else
					{
						maxToaster = 0;
					}
				}
				else
				{
					startYPosition = screenRect.y - toasterHeight;
					stopYPosition = screenRect.y;

					if ( currentNumberOfToaster > 0 )
					{
						stopYPosition = stopYPosition + ( maxToaster % maxToasterInSceen * toasterHeight );
					}
					else
					{
						maxToaster = 0;
					}
				}

				currentNumberOfToaster++;
				maxToaster++;


				animateVertically( posx, startYPosition, stopYPosition );
				Thread.sleep(displayTime);
				animateVertically( posx, stopYPosition, startYPosition );

				currentNumberOfToaster--;
				toaster.setVisible(false);
				toaster.dispose();
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
